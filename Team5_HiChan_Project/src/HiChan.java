package Hi_Chan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

     // Keep the GPS header and line ending so the output uses the same format.
    private static String gpsHeader;
    private static String lineEnding = "\n";
    
    /*
     * How to code it:
     *   1. If args.length != 3, print
     *        Usage: java Hi_Chan.HiChan <lookup.csv> <gps.csv> <out.csv>
     *      to System.err and System.exit(1).
     *   2. If args[2] is the same file as args[0] or args[1], refuse it (NFR-S1).
     *   3. Map<String, List<Hi_Chan.HighwaySegment>> index = loadIndex(args[0]);
     *   4. List<Hi_Chan.GpsRecord> records = readGpsLog(args[1]);
     *   5. processRecords(records, index);
     *   6. backfillChannelIds(records);
     *   7. writeOutput(records, args[2]);
     *   8. Print a short summary, e.g. "Labeled 128 of 128 entries".
     *   9. Catch IOException: print ONE line to System.err,
     *        ERROR:<file name>:<e.getMessage()>
     *      with no stack trace, and System.exit(1) (NFR-U2).
     *   Run it once for the EB file and once for the WB file.
     */
    public static void main(String[] args) {
        // TODO: wire the steps together
        // Three file names are required: lookup, GPS input, and output.
        if (args.length != 3){
            System.err.println("Usage: java Hi_Chan.HiChan <lookup.csv> <gps.csv> <out.csv>");
            System.exit(1);
            
            return;
        }

        // Remember which file is being used so errors name the right one.
        String file = args[2];
        try {
            // Refuse either input, including another path or link to the same file.
            Path output = Paths.get(args[2]).toAbsolutePath().normalize();
            for (int i = 0; i < 2; i++) {
                Path input = Paths.get(args[i]).toAbsolutePath().normalize();
                if (output.equals(input)
                        || (Files.exists(output) && Files.exists(input) && Files.isSameFile(output, input)))
                    throw new IOException("output file is an input file");
            }

            // Read both inputs, match the readings, and fill in unknown directions.
            file = args[0];
            Map<String, List<HighwaySegment>> index = loadIndex(args[0]);
            file = args[1];
            List<GpsRecord> records = readGpsLog(args[1]);
            processRecords(records, index);
            backfillChannelIds(records);

            file = args[2];
            writeOutput(records, args[2]);

            // Count labeled records without counting skipped lines.
            int labeled = 0;
            for (GpsRecord record : records)
                if (record.getErrorText() == null && record.getChannelId() != null)
                    labeled++;
            System.out.println("Labeled " + labeled + " of " + records.size() + " entries");
        } catch (IOException e) {
            System.err.println("ERROR:" + file + ":" + e.getMessage());
            System.exit(1);
            
        }
    } // end of main

    /*
     * What it does: reads the Hi_Chan.Preprocessor CSV into a HashMap so lookups by
     * geohash are fast:  geohash -> every highway point in that cell.
     *
     * How to code it:
     *   1. Throw new IOException(reason) when the file can't be used, with reason:
     *        "file not found", "file not readable", "file empty" (no rows),
     *        "wrong header line" (must be GEOHASH,ROUTE,STFIPS,BEGMP,ENDMP,LAT,LON,CHANNEL),
     *        "lookup file not sorted" (a geohash smaller than the one before it).
     *   2. Read the file line by line; trim each line so CRLF files work. Skip the header.
     *   3. Hi_Chan.HighwaySegment seg = Hi_Chan.HighwaySegment.fromCsvLine(line);
     *   4. index.computeIfAbsent(seg.getGeohash(), k -> new ArrayList<>()).add(seg);
     *   5. return index;
     */
    public static Map<String, List<HighwaySegment>> loadIndex(String path) throws IOException {
        Map<String, List<HighwaySegment>> index = new HashMap<>();
        // TODO: read CSV and build geohash -> points map
        // Check the file before trying to read its rows.
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
            int lineNumber = 1;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;

                // A bad lookup row stops the run and reports its line number.
                HighwaySegment segment;
                try {
                    segment = HighwaySegment.fromCsvLine(line);
                } catch (IllegalArgumentException e){
                    throw new IOException("bad row at line " + lineNumber);
                }

                // Geohashes must stay in ascending order; repeated hashes are allowed.
                String hash = segment.getGeohash();
                if (previousHash != null && hash.compareTo(previousHash) < 0)
                    throw new IOException("lookup file not sorted");
                index.computeIfAbsent(hash, k -> new ArrayList<>()).add(segment);
                previousHash = hash;
            }
        }
        if (index.isEmpty()) throw new IOException("file empty");
        
        return index;
    } // end of loadIndex

    /*
     * What it does: reads a GPS Logger CSV into a list of Hi_Chan.GpsRecord objects.
     *
     * How to code it:
     *   1. Throw new IOException(reason) with "file not found", "file not readable",
     *      "file empty" or "wrong header line". The header must be:
     *        date time,latitude,longitude,altitude(m),speed(m/s),sat_used
     *   2. Remember the header and the line ending ("\r\n" if the file has one, else "\n")
     *      in static fields so writeOutput can reuse them (NFR-D1).
     *   3. Skip blank lines.
     *   4. records.add(Hi_Chan.GpsRecord.fromCsvLine(line));
     *      If it throws IllegalArgumentException, keep the line in its place instead:
     *        GpsRecord bad = new GpsRecord(line, null, 0, 0, 0, 0, 0);
     *        bad.setErrorText(e.getMessage());   // FR-14
     *   5. return records;
     */
    public static List<GpsRecord> readGpsLog(String path) throws IOException {
        List<GpsRecord> records = new ArrayList<>();
        // TODO: parse every data line
        Path file = Paths.get(path);
        
        if (!Files.exists(file))
            throw new IOException("file not found");
        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IOException("file not readable");

        // Read the original text so Windows and Unix line endings can be preserved.
        String text = Files.readString(file, StandardCharsets.UTF_8);
        
        if (text.trim().isEmpty())
            throw new IOException("file empty");
        String[] lines = text.split("\\r?\\n", -1);
        if (!lines[0].equals("date time,latitude,longitude,altitude(m),speed(m/s),sat_used"))
            throw new IOException("wrong header line");
        gpsHeader = lines[0];
        lineEnding = text.contains("\r\n") ? "\r\n" : "\n";

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty())
                continue;

            // Keep bad lines in their original positions with the parser's reason.
            try {
                records.add(GpsRecord.fromCsvLine(line));
            } catch (IllegalArgumentException e) {
                GpsRecord bad = new GpsRecord(line, null, 0, 0, 0, 0, 0);
                bad.setErrorText(e.getMessage());
                records.add(bad);
            }
        }
        if (records.isEmpty()) throw new IOException("file empty");
        
        return records;
    } // end of readGpsLog

    /*
     * What it does: the main algorithm. Matches every record to a highway point,
     * then builds its Channel ID. (main calls backfillChannelIds afterwards.)
     *
     * How to code it:
     *     for i = 0 .. records.size()-1:
     *        r = records.get(i); skip it if r.getErrorText() != null
     *        r.setGeohash(Hi_Chan.GeoHash.encode(r.getLatitude(), r.getLongitude()))
     *        seg = findBestSegment(r, index, previous point)
     *        if seg == null: r.setErrorText("no I-90 point near reading") and continue
     *        r.setSegment(seg)
     *        if r.isSameLocation(previous good record) and that record's Channel ID
     *           has a known direction (not ??) -> reuse its segment and Channel ID (FR-11)
     *        else r.setChannelId(buildChannelId(seg, determineDirection(records, i)))
     */
    public static void processRecords(List<GpsRecord> records, Map<String, List<HighwaySegment>> index) {
        // TODO: match and label every record
        GpsRecord previous = null;
        for (int i = 0; i < records.size(); i++) {
            GpsRecord record = records.get(i);
            if (record.getErrorText() != null)
                continue;

            // Match this reading, using the previous state to break an exact tie.
            record.setGeohash(GeoHash.encode(record.getLatitude(), record.getLongitude()));
            HighwaySegment segment = findBestSegment(record, index, previous == null ? null : previous.getSegment());
            
            if (segment == null) {
                record.setErrorText("no I-90 point near reading");
                continue;
            }
            record.setSegment(segment);

            // A stopped truck keeps its last known Channel ID and highway point.
            if (record.isSameLocation(previous) && previous.getChannelId() != null && !previous.getChannelId().contains("??")) {
                record.setSegment(previous.getSegment());
                record.setChannelId(previous.getChannelId());
            } else {
                record.setChannelId(buildChannelId(segment, determineDirection(records, i)));
            }
            previous = record;
        }
        
    } // end of processRecords

    /*
     * What it does: picks the highway point nearest the GPS reading (FR-06).
     *
     * How to code it:
     *   1. List<Hi_Chan.HighwaySegment> candidates = index.get(record's geohash)
     *      (encode it yourself if record.getGeohash() is null).
     *   2. If candidates is null or empty, collect the points in the 8 cells from
     *      Hi_Chan.GeoHash.neighbors(...) instead (fallback only, AD-2).
     *      If there are still none, return null.
     *   3. Return the candidate with the smallest
     *      Hi_Chan.GeoHash.haversineMiles(reading lat, lon, point lat, lon).
     *      On an exact tie, prefer the point in the same state as `previous`.
     */
    public static HighwaySegment findBestSegment(GpsRecord record,
                                                 Map<String, List<HighwaySegment>> index,
                                                 HighwaySegment previous) {
        // TODO: look up candidates and choose the nearest

        String hash = record.getGeohash();
        if (hash == null) {
            hash = GeoHash.encode(record.getLatitude(), record.getLongitude());
            record.setGeohash(hash);
        }

        // Only search neighboring cells when the reading's own cell is empty.
        List<HighwaySegment> candidates = index.get(hash);
        if (candidates == null || candidates.isEmpty()) {
            candidates = new ArrayList<>();
            for (String neighbor : GeoHash.neighbors(hash)) {
                List<HighwaySegment> nearby = index.get(neighbor);
                if (nearby != null)
                    candidates.addAll(nearby);
            }
        }

        // Keep the closest point; an exact tie favors the previous point's state.
        HighwaySegment best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (HighwaySegment candidate : candidates) {
            double distance = GeoHash.haversineMiles(record.getLatitude(), record.getLongitude(), candidate.getLat(), candidate.getLon());
            
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && previous != null
                        && candidate.getStfips().equals(previous.getStfips())
                        && !best.getStfips().equals(previous.getStfips()))) {
                best = candidate;
                bestDistance = distance;
            }
        }
        
        return best;
    } // end of findBestSegment

    /*
     * What it does: returns "EB", "WB" or "??" for record i (FR-09, FR-10).
     *
     * How to code it:
     *   1. Find the two good records before i (skip any with errorText or no segment).
     *      If there aren't two, return "??".
     *   2. a = segment of the record two back, c = segment of record i.
     *   3. STATE CHANGE CHECK first (mileposts reset at the border):
     *        - a is WA ("53") and c is ID ("16") -> "EB"
     *        - a is ID and c is WA               -> "WB"
     *   4. Otherwise compare mileposts:
     *        - c.getBegMp() > a.getBegMp() (or equal BEGMP and bigger ENDMP) -> "EB"
     *        - c.getBegMp() < a.getBegMp() (or equal BEGMP and smaller ENDMP) -> "WB"
     *   5. Same segment: return the direction in the previous good record's Channel ID
     *      (the two letters before "_"), or "??" if it has none.
     */
    public static String determineDirection(List<GpsRecord> records, int i) {
        // TODO: decide EB / WB / ??
        GpsRecord current = records.get(i);
        if (current.getErrorText() != null || current.getSegment() == null)
            return "??";

        // Count back through good readings, ignoring malformed or unmatched lines.
        GpsRecord previous = null;
        GpsRecord twoBack = null;
        
        for (int j = i - 1; j >= 0; j--) {
            GpsRecord record = records.get(j);
            if (record.getErrorText() != null || record.getSegment() == null)
                continue;
            if (previous == null) {
                previous = record;
            } else {
                twoBack = record;
                break;
            }
        }
        if (twoBack == null)
            return "??";

        HighwaySegment a = twoBack.getSegment();
        HighwaySegment c = current.getSegment();

        // State changes come first because mileposts reset at the WA/ID border.
        if (HiChanConfig.STATE_WA.equals(a.getStfips()) && HiChanConfig.STATE_ID.equals(c.getStfips()))
            return "EB";
        if (HiChanConfig.STATE_ID.equals(a.getStfips()) && HiChanConfig.STATE_WA.equals(c.getStfips()))
            return "WB";

        // Compare this point with the point two good readings back.
        if (c.getBegMp() > a.getBegMp()
                || (c.getBegMp() == a.getBegMp() && c.getEndMp() > a.getEndMp()))
            return "EB";
        if (c.getBegMp() < a.getBegMp()
                || (c.getBegMp() == a.getBegMp() && c.getEndMp() < a.getEndMp()))
            return "WB";

        // The same segment keeps the previous direction, if one is known.
        String id = previous.getChannelId();
        if (id != null && id.contains("EB_"))
            return "EB";
        if (id != null && id.contains("WB_"))
            return "WB";
        return "??";
    } // end of determineDirection

    /*
     * What it does: converts a milepost into its 10-mile channel number (FR-08).
     *   0 to 10  -> 10
     *   10.1 to 20 -> 20
     *   20.1 to 30 -> 30   ... and so on
     *
     * How to code it:
     *   1. int channel = (int) Math.ceil(milepost / HiChanConfig.CHANNEL_MILES) * HiChanConfig.CHANNEL_MILES;
     *   2. if channel == 0 (milepost exactly 0) return 10.
     *   3. return channel;
     *   Milepost exactly 10.0 gives 10 (inclusive upper bound).
     */
    public static int computeChannelNumber(double milepost) {
        // TODO: round milepost up to the next multiple of 10
        // Round up. Keeping exact boundaries in the lower channel.
        int channel = (int) Math.ceil(milepost / HiChanConfig.CHANNEL_MILES) * HiChanConfig.CHANNEL_MILES;
        return channel == 0 ? HiChanConfig.CHANNEL_MILES : channel;
    } // end of computeChannelNumber

    /*
     * What it does: assembles the Channel ID, e.g. "#I90WAEB_290" (FR-13).
     *
     * How to code it:
     *   1. If point == null, return null. If direction == null, use "??".
     *   2. return HiChanConfig.CHANNEL_PREFIX + point.getRoute() + point.getStateAbbrev()
     *             + direction + "_" + point.getChannel();
     */
    public static String buildChannelId(HighwaySegment point, String direction) {
        // TODO: build the Channel ID string
        if (point == null)
            return null;
        if (direction == null)
            direction = "??";

        // Use the channel already assigned to this highway point by the Preprocessor.
        return HiChanConfig.CHANNEL_PREFIX + point.getRoute() + point.getStateAbbrev() + direction + "_" + point.getChannel();
    } // end of buildChannelId

    /*
     * What it does: replaces "??" once direction is known (FR-12).
     *
     * How to code it:
     *   1. Find the first record whose Channel ID has a known direction
     *      (the two letters before "_" are "EB" or "WB"). If none, leave everything
     *      as is: a log with only two entries keeps ?? (FR-10).
     *   2. Every earlier "??" record gets buildChannelId(itsOwnSegment, thatDirection).
     *   3. Any later "??" record takes the most recent known direction.
     *   Skip records with errorText.
     */
    public static void backfillChannelIds(List<GpsRecord> records) {
        // TODO: fill in ?? once direction is known
        // Find the first known direction so the earlier readings can use it.
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
        }
        if (direction == null)
            return;

        // Each reading keeps its own state and channel; only the direction is filled in.
        for (GpsRecord record : records) {
            String id = record.getChannelId();
            if (record.getErrorText() != null || id == null)
                continue;
            if (id.contains("??_"))
                record.setChannelId(buildChannelId(record.getSegment(), direction));
            else if (id.contains("EB_"))
                direction = "EB";
            else if (id.contains("WB_"))
                direction = "WB";
        }
    } // end of backfillChannelIds

    /*
     * What it does: writes the updated GPS file. Same lines as the input, in the
     * same order, with the Channel ID as the last field (FR-12).
     *
     * How to code it:
     *   1. Open a PrintWriter with try-with-resources.
     *   2. Write the saved header + ",channel_id", using the saved line ending.
     *   3. For each record: write record.toOutputLine() + the saved line ending.
     */
    public static void writeOutput(List<GpsRecord> records, String path) throws IOException {
        // TODO: write header + every record
        
        // Print the saved line ending explicitly instead of using the system default.
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8))) {
            writer.print(gpsHeader + ",channel_id" + lineEnding);
            
            for (GpsRecord record : records)
                writer.print(record.toOutputLine() + lineEnding);

            // PrintWriter stores write failures, so check before reporting success.
            if (writer.checkError())
                throw new IOException("cannot write file");
        }
    } // end of writeOutput
} // end of Hichan
