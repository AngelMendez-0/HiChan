package Hi_Chan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hi_Chan.HiChan.java ("Highway Channel")
 *
 * Reads a GPS log, finds the nearest I-90 point for each reading using the
 * Hi_Chan.Preprocessor's CSV, works out direction of travel, and writes the GPS
 * log back out with a Channel ID appended as the last field.
 *
 * Channel ID format (FR-13):  # + I90 + STATE + DIR + "_" + CHANNEL
 *     #I90WAEB_290  -> eastbound I-90 in Washington, milepost 280-290
 *     #I90IDWB_10   -> westbound I-90 in Idaho, milepost 0-10
 *     #I90WA??_290  -> direction not known yet (FR-10)
 *
 * Rules from the vision statement:
 *   - Mileposts INCREASE going east and DECREASE going west (within a state).
 *   - Crossing the WA/ID line resets mileposts (Idaho starts at 0).
 *     WA -> ID means eastbound; ID -> WA means westbound.
 *   - Direction needs 3 GPS points: entry i is compared with entry i-2 (AD-6).
 *     Earlier entries get ?? and are back-filled once direction is known (FR-12).
 *   - If the GPS point repeats (truck stopped), reuse the prior Channel ID (FR-11).
 *   - A bad line stays in place ending with SKIPPED-Error:<reason> (FR-14).
 *
 * Usage: java Hi_Chan.HiChan segments.csv "20260922-160725 - I90EB.csv" I90EB_out.csv
 */
public class HiChan {

    // Keep the GPS header and line ending so the output uses the same format
    private static String gpsHeader;
    private static String lineEnding = "\n";
    
    // Runs the program: loads the lookup file, reads the GPS log, labels every entry,
    // fills in ?? once direction is known and writes the output file
    // Any problem prints one line, ERROR:<file>:<reason>, and exits with status 1 
    public static void main(String[] args) {
        // Three file names are required: lookup, GPS input, and output
        if (args.length != 3){
            System.err.println("Usage: java Hi_Chan.HiChan <lookup.csv> <gps.csv> <out.csv>");
            System.exit(1);
            
            return;
        } // end of if

        // Remember which file is being used so errors name the right one
        String file = args[2];
        try {
            // Refuse either input, including another path or link to the same file
            Path output = Paths.get(args[2]).toAbsolutePath().normalize();
            for (int i = 0; i < 2; i++) {
                Path input = Paths.get(args[i]).toAbsolutePath().normalize();
                if (output.equals(input)
                        || (Files.exists(output) && Files.exists(input) && Files.isSameFile(output, input)))
                    throw new IOException("output file is an input file");
            } // end of for

            // Read both inputs, match the readings, and fill in unknown directions
            file = args[0];
            Map<String, List<HighwaySegment>> index = loadIndex(args[0]);
            file = args[1];
            List<GpsRecord> records = readGpsLog(args[1]);
            processRecords(records, index);
            backfillChannelIds(records);

            file = args[2];
            writeOutput(records, args[2]);

            // Count labeled records without counting skipped lines
            int labeled = 0;
            for (GpsRecord record : records)
                if (record.getErrorText() == null && record.getChannelId() != null)
                    labeled++;
            System.out.println("Labeled " + labeled + " of " + records.size() + " entries");
        } catch (IOException e) {
            System.err.println("ERROR:" + file + ":" + e.getMessage());
            System.exit(1);
        } catch (InvalidPathException e) {
            System.err.println("ERROR:" + file + ":invalid file name");
            System.exit(1);
            
        } // end of try catch
    } // end of main

    // Reads the Preprocessor's lookup CSV into a map of geohash to every highway point
    // in that cell, so lookups are fast. Throws IOException if the file is missing,
    // unreadable, empty, has the wrong header or is not sorted by geohash
    public static Map<String, List<HighwaySegment>> loadIndex(String path) throws IOException {
        Map<String, List<HighwaySegment>> index = new HashMap<>();
        // Check the file before trying to read its rows
        Path file = Paths.get(path);
        if (!Files.exists(file))
            throw new IOException("file not found");
        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IOException("file not readable");

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null)
                throw new IOException("file empty");
            if (!header.trim().equals("GEOHASH,ROUTE,STFIPS,BEGMP,ENDMP,LAT,LON,CHANNEL"))
                throw new IOException("wrong header line");

            String previousHash = null;
            String line;
            // Line 1 is the header
            int lineNumber = 1;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;

                // A bad lookup row stops the run and reports its line number
                HighwaySegment segment;
                try {
                    segment = HighwaySegment.fromCsvLine(line);
                } catch (IllegalArgumentException e){
                    throw new IOException("bad row at line " + lineNumber);
                } // end of try catch

                // Geohashes must stay in ascending order; repeated hashes are allowed
                String hash = segment.getGeohash();
                if (previousHash != null && hash.compareTo(previousHash) < 0)
                    throw new IOException("lookup file not sorted");
                // Start a new list the first time a geohash is seen, then add this point to it
                index.computeIfAbsent(hash, k -> new ArrayList<>()).add(segment);
                previousHash = hash;
            } // end of while
        } // end of try
        if (index.isEmpty()) throw new IOException("file empty");
        
        return index;
    } // end of loadIndex

    // Reads a GPS Logger CSV into a list of GpsRecords and remembers its header and line
    // ending for the output (NFR-D1). A bad line is kept in its place with its error
    // text and reported on the console with its line number
    public static List<GpsRecord> readGpsLog(String path) throws IOException {
        List<GpsRecord> records = new ArrayList<>();
        Path file = Paths.get(path);
        
        if (!Files.exists(file))
            throw new IOException("file not found");
        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IOException("file not readable");

        // Read the original text so Windows and Unix line endings can be preserved
        String text = Files.readString(file, StandardCharsets.UTF_8);
        
        if (text.trim().isEmpty())
            throw new IOException("file empty");
        // Split on Windows (\r\n) or Unix (\n) line breaks; -1 keeps empty lines at the end
        String[] lines = text.split("\\r?\\n", -1);
        if (!lines[0].trim().equals("date time,latitude,longitude,altitude(m),speed(m/s),sat_used"))
            throw new IOException("wrong header line");
        gpsHeader = lines[0];
        // Use the same line ending as the input file for the output
        lineEnding = text.contains("\r\n") ? "\r\n" : "\n";

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty())
                continue;

            // Keep bad lines in their original positions with the parser's reason
            try {
                records.add(GpsRecord.fromCsvLine(line));
            } catch (IllegalArgumentException e) {
                // Placeholder record that only keeps the original text so the line stays in its place
                GpsRecord bad = new GpsRecord(line, null, 0, 0, 0, 0, 0);
                bad.setErrorText(e.getMessage());
                records.add(bad);
                // I + 1 because file lines are counted from 1
                System.out.println("Skipped line " + (i + 1) + ": " + e.getMessage());
            } // end of try catch
        } // end of for
        if (records.isEmpty()) throw new IOException("file empty");
        
        return records;
    } // end of readGpsLog

    // Matches every good record to its nearest highway point and builds its Channel ID
    // A stopped truck reuses the previous Channel ID once direction is known
    public static void processRecords(List<GpsRecord> records, Map<String, List<HighwaySegment>> index) {
        GpsRecord previous = null;
        for (int i = 0; i < records.size(); i++) {
            GpsRecord record = records.get(i);
            if (record.getErrorText() != null)
                continue;

            // Match this reading, using the previous state to break an exact tie
            record.setGeohash(GeoHash.encode(record.getLatitude(), record.getLongitude()));
            // The first reading has no previous point to compare with
            HighwaySegment segment = findBestSegment(record, index, previous == null ? null : previous.getSegment());
            
            if (segment == null) {
                record.setErrorText("no I-90 point near reading");
                continue;
            } // end of if
            record.setSegment(segment);

            // A stopped truck keeps its last known Channel ID and highway point
            if (record.isSameLocation(previous) && previous.getChannelId() != null && !previous.getChannelId().contains("??")) {
                record.setSegment(previous.getSegment());
                record.setChannelId(previous.getChannelId());
            } else {
                record.setChannelId(buildChannelId(segment, determineDirection(records, i)));
            } // end of if
            previous = record;
        } // end of for
        
    } // end of processRecords

    // Returns the highway point nearest the GPS reading (FR-06). Searches the reading's own
    // geohash cell first and the 8 neighboring cells only if it is empty. An exact tie
    // goes to the point in the same state as the previous reading
    public static HighwaySegment findBestSegment(GpsRecord record,
                                                 Map<String, List<HighwaySegment>> index,
                                                 HighwaySegment previous) {

        String hash = record.getGeohash();
        if (hash == null) {
            hash = GeoHash.encode(record.getLatitude(), record.getLongitude());
            record.setGeohash(hash);
        } // end of if

        // Only search neighboring cells when the reading's own cell is empty
        List<HighwaySegment> candidates = index.get(hash);
        if (candidates == null || candidates.isEmpty()) {
            candidates = new ArrayList<>();
            for (String neighbor : GeoHash.neighbors(hash)) {
                List<HighwaySegment> nearby = index.get(neighbor);
                if (nearby != null)
                    candidates.addAll(nearby);
            } // end of for
        } // end of if

        // Keep the closest point; an exact tie favors the previous point's state
        HighwaySegment best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (HighwaySegment candidate : candidates) {
            double distance = GeoHash.haversineMiles(record.getLatitude(), record.getLongitude(), candidate.getLat(), candidate.getLon());
            
            // Take this point if it is closer, or if it ties and is in the previous reading's state
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && previous != null
                        && candidate.getStfips().equals(previous.getStfips())
                        && !best.getStfips().equals(previous.getStfips()))) {
                best = candidate;
                bestDistance = distance;
            } // end of if
        } // end of for
        
        return best;
    } // end of findBestSegment

    // Returns "EB", "WB" or "??" for record i by comparing it with the good record two
    // back A WA to ID crossing is EB and ID to WA is WB otherwise
    // increasing mileposts are EB and decreasing are WB
    public static String determineDirection(List<GpsRecord> records, int i) {
        GpsRecord current = records.get(i);
        if (current.getErrorText() != null || current.getSegment() == null)
            return "??";

        // Count back through good readings, ignoring malformed or unmatched lines
        GpsRecord previous = null;
        GpsRecord twoBack = null;
        
        // Walk backward until the two good readings before this one are found
        for (int j = i - 1; j >= 0; j--) {
            GpsRecord record = records.get(j);
            if (record.getErrorText() != null || record.getSegment() == null)
                continue;
            if (previous == null) {
                previous = record;
            } else {
                twoBack = record;
                break;
            } // end of if
        } // end of for
        if (twoBack == null)
            return "??";

        // A = point two good readings back, c = this reading's point
        HighwaySegment a = twoBack.getSegment();
        HighwaySegment c = current.getSegment();

        // State changes come first because mileposts reset at the WA/ID border
        if (HiChanConfig.STATE_WA.equals(a.getStfips()) && HiChanConfig.STATE_ID.equals(c.getStfips()))
            return "EB";
        if (HiChanConfig.STATE_ID.equals(a.getStfips()) && HiChanConfig.STATE_WA.equals(c.getStfips()))
            return "WB";

        // Compare this point with the point two good readings back
        // If BEGMP is the same, ENDMP decides
        if (c.getBegMp() > a.getBegMp()
                || (c.getBegMp() == a.getBegMp() && c.getEndMp() > a.getEndMp()))
            return "EB";
        if (c.getBegMp() < a.getBegMp()
                || (c.getBegMp() == a.getBegMp() && c.getEndMp() < a.getEndMp()))
            return "WB";

        // The same segment keeps the previous direction, if one is known
        // Read the direction from the previous Channel ID, e.g. "EB_" in #I90WAEB_290
        String id = previous.getChannelId();
        if (id != null && id.contains("EB_"))
            return "EB";
        if (id != null && id.contains("WB_"))
            return "WB";
        return "??";
    } // end of determineDirection

    // Turns a milepost into its 10-mile channel number (FR-08):
    // 0 to 10 -> 10, 10.1 to 20 -> 20, 20.1 to 30 -> 30 and so on
    public static int computeChannelNumber(double milepost) {
        // Round up, e.g. 6.8 / 10 = 0.68 -> 1 -> channel 10; an exact 10.0 stays in channel 10
        int channel = (int) Math.ceil(milepost / HiChanConfig.CHANNEL_MILES) * HiChanConfig.CHANNEL_MILES;
        // Milepost 0 rounds to 0, so it belongs to channel 10
        return channel == 0 ? HiChanConfig.CHANNEL_MILES : channel;
    } // end of computeChannelNumber

    // Builds the Channel ID for a highway point and direction, e.g. "#I90WAEB_290" (FR-13)
    public static String buildChannelId(HighwaySegment point, String direction) {
        if (point == null)
            return null;
        if (direction == null)
            direction = "??";

        // Use the channel already assigned to this highway point by the Preprocessor
        return HiChanConfig.CHANNEL_PREFIX + point.getRoute() + point.getStateAbbrev() + direction + "_" + point.getChannel();
    } // end of buildChannelId

    // Replaces ?? with the direction once it is known (FR-12). A log where direction is
    // never known keeps ?? (FR-10)
    public static void backfillChannelIds(List<GpsRecord> records) {
        // Find the first known direction so the earlier readings can use it
        String direction = null;
         
        for (GpsRecord record : records){
            String id = record.getChannelId();
             
            if (record.getErrorText() != null || id == null)
                continue;
             
            if (id.contains("EB_"))
                direction = "EB";
                 
            else if (id.contains("WB_"))
                direction = "WB";
             
            if (direction != null)
                break;
             
        } // end of for
        if (direction == null)
            return;

        // Each reading keeps its own state and channel; only the direction is filled in
        for (GpsRecord record : records) {
            String id = record.getChannelId();
          
            if (record.getErrorText() != null || id == null)
                continue;
            // Still unknown, so rebuild it with the direction found
            if (id.contains("??_"))
                record.setChannelId(buildChannelId(record.getSegment(), direction));
                 
            else if (id.contains("EB_"))
                direction = "EB";
                 
            else if (id.contains("WB_"))
                direction = "WB";
        } // end of for
         
    } // end of backfillChannelIds

    // Writes the output file: the GPS log's lines in the same order with the Channel ID
    // added as the last field using the input's line ending
    public static void writeOutput(List<GpsRecord> records, String path) throws IOException {
        
        // Print the saved line ending explicitly instead of using the system default
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8))) {
            // Header line gets the new channel_id column
            writer.print(gpsHeader + ",channel_id" + lineEnding);
            
            for (GpsRecord record : records)
                writer.print(record.toOutputLine() + lineEnding);

            // PrintWriter stores write failures, so check before reporting success
            if (writer.checkError())
                throw new IOException("cannot write file");
        } catch (IOException | InvalidPathException e) {
            // Report any write problem with the same short message
            throw new IOException("cannot write file");
        } // end of try catch
    } // end of writeOutput
     
} // end of Hichan
