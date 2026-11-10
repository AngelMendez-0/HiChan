package Hi_Chan;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileWriter;
import java.io.PrintWriter;

/*
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣠⡴⠚⢉⡃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⣉⠛⠻⢶⢦⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⠖⢫⢗⣴⡿⣻⠟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠑⢰⣜⣎⣎⠷⢄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⠞⢁⣴⢣⣫⠌⣽⢃⣀⡤⠤⠀⠀⠀⠀⠀⠀⠀⠤⠤⠤⢤⣀⣠⡟⣟⣘⠘⡆⠑⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠂⠀⡴⠃⣰⡿⣴⡧⠷⠚⠉⠁⣀⣠⠤⢤⣒⣒⣂⣀⣠⣠⣄⣠⣤⣶⣿⡟⠙⠻⢼⣾⡞⡆⠈⣷⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⡜⢁⣾⠟⠋⠙⣿⣶⣶⣶⣷⣶⣶⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣶⣶⣦⣤⣌⣙⠿⣕⡀⠿⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣎⠴⠋⠀⣠⠔⣢⣼⣿⣿⣿⣿⣿⡿⠉⢿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡿⠟⠛⢫⡙⠈⠳⣄⢻⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⡄⠀⠀⣠⠞⠁⢀⡤⣚⣵⣿⣿⣿⣿⣿⣿⣿⠏⠀⠀⠀⢻⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣦⣄⠈⠲⣄⠈⣳⣇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⢿⣶⣴⣉⣀⣴⣫⣾⣿⣿⣿⣿⣿⡟⣿⣿⠋⠀⠀⠀⠀⠀⢿⣿⣿⣿⣿⠘⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣆⡈⢧⡈⢙⣆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⢿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡟⢸⣿⠇⠀⠀⠀⠀⠀⠀⠀⢻⣿⣿⣿⠀⠘⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡀⠙⣄⠹⣦⡀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⠋⠀⡙⣿⣿⣿⣿⣿⣿⣿⣿⣿⠀⣸⣟⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⣿⠀⠀⠘⣿⣿⣿⡿⣿⣿⣿⣿⣿⣿⡟⢿⡜⣎⢦⠙⣷⡀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⡼⠃⢠⣯⣾⣿⣿⣿⣿⣿⣿⣿⣿⠁⠀⣻⡧⣀⠀⠀⠀⠀⠀⠀⠀⠀⠸⣿⣿⢀⣤⠶⢻⣿⣿⡷⢿⣿⣿⣿⣿⣿⣿⣴⡿⡜⣾⡇⠈⣷⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⣼⠁⢠⣿⣿⠟⠋⣽⣿⣿⣿⣿⣿⣧⠶⠚⢻⡏⠛⠲⢦⣀⠀⠀⣠⠀⠀⠀⢿⣿⠋⠀⠀⠸⣿⣿⠀⢸⣿⣿⣿⣿⣿⣿⣿⡅⠙⠀⠘⣄⠙⣧⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⢸⠃⠀⣼⡟⠁⠀⣠⣿⣿⣿⣿⣿⣿⠁⠀⠀⠈⢇⠀⠀⠉⠈⠑⠀⠙⢦⠀⠀⢸⡏⠀⢀⣠⣶⣿⠇⠀⠀⣿⣿⣿⣿⣿⣿⣿⣿⣄⠀⠀⢸⠀⠸⡇⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⢸⠀⠀⡇⠁⠄⣰⣿⣿⣿⣿⣿⣿⣇⠀⠀⠠⣄⣿⣶⣦⣄⡄⠀⠀⢄⣿⠆⠀⠘⠃⣰⣿⣷⡿⠿⠶⣦⡀⣿⣿⢻⣿⣿⣿⣿⡝⠻⣦⣠⠀⠇⠀⢳⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⢸⠇⠀⢁⣴⡾⠿⢻⣿⣿⣿⣿⣿⣿⠀⣠⣾⠟⠉⠠⠶⠛⠇⠀⠀⣿⠁⠀⠀⠀⠈⠛⣙⣉⣉⣀⣠⡴⠛⣿⡏⣿⣿⣿⣿⣿⡇⠀⠀⠙⠈⢡⠀⣼⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⡖⣾⠄⠀⠀⠀⠀⠀⣿⣿⣿⣿⣿⣟⢿⡇⠀⠁⠀⠀⠀⠀⠀⠀⠀⠀⠉⠣⠘⠂⠀⣀⣐⣀⣈⣁⡤⠤⠷⠶⣟⠀⣿⣿⣿⣿⣿⡇⠀⠀⠀⠀⠱⠀⣧⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠁⢯⡆⠐⡄⠀⠀⠀⣾⣿⡇⣿⣿⣿⣆⢹⣷⠞⠚⠋⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠉⠁⠀⠀⢀⠀⠀⢀⠀⢸⢀⣿⣿⣿⣿⠹⣇⠀⠀⠀⠀⡇⠀⣸⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⢸⡃⠀⢳⢰⡀⣄⠈⣿⡀⣼⣛⢻⡏⣿⠃⠀⡆⠀⢠⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⡇⠀⣸⡤⢸⢸⡿⣿⢻⣿⡄⠉⠀⠀⠀⢸⠀⢠⡏⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⢧⠀⠈⣧⢷⡙⣮⢯⡧⣇⣯⣷⢹⣸⡆⠦⢽⣄⣘⡆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠤⠤⠒⠚⡟⠋⠹⢀⡟⡾⣾⣿⢸⠈⢻⣦⣄⣠⢄⡟⣀⡞⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠈⢣⣤⠘⢿⡻⠈⠉⠁⠹⣜⢯⡓⢧⢳⡀⢸⡁⠀⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠀⢀⡼⣽⢗⣫⢏⡞⠀⠛⠿⡿⢃⡞⠀⡞⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠳⣅⠀⠝⣆⠺⣔⢴⣬⣳⢭⣩⣦⢳⣄⠀⠀⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⡞⣱⢗⣋⡽⢋⡀⠀⠀⡀⣠⠊⣰⡜⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠢⣄⠈⢳⣌⡛⠋⠉⠁⠀⠈⠳⢼⡷⢤⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣤⡾⢋⣴⡿⠉⠱⣶⠿⢟⡵⣺⠞⢁⣴⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠓⠤⣀⢀⠀⢦⡿⣔⠯⣷⠆⢻⣶⣬⣳⠲⠤⣄⣀⣀⣀⣤⠤⠤⢖⡻⣟⣭⣴⣿⠏⢳⣺⠴⢀⡀⢉⡜⡁⣠⠞⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠻⢥⣀⠉⠻⣋⠀⠀⠈⠓⢻⢎⠙⠒⠯⣄⡀⢀⣐⡶⠾⠛⠋⡽⣸⠃⠉⠀⠀⠀⢚⡩⠞⢡⣾⠟⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⡬⠵⣶⡤⣄⡐⠦⠄⢸⢸⠀⠀⠀⠀⠉⠉⠀⠀⠀⠀⣸⠁⡏⠄⣀⣀⣤⠮⣭⣤⠚⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⡾⢃⣴⣿⣿⣿⣿⣿⣷⣦⡟⢸⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⡏⠀⣷⣛⣉⣬⣤⣤⡈⠹⢳⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⣠⠴⠚⠉⠛⠒⢲⡏⣰⣿⣿⣿⣿⣿⣿⣿⣿⣿⠃⡞⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣧⠀⢻⣿⣿⣿⣿⣿⣿⣧⠈⠙⠦⣄⡤⠴⠦⣤⡀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⢀⡞⢉⣴⣾⣿⣿⣷⣿⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⡏⣰⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⡀⠸⣿⣿⣿⣿⣿⣿⣿⣷⣄⣤⣤⣤⣴⣶⣭⡙⢦⡀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⢠⠏⣠⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⠟⡴⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢳⡀⠹⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⡄⠱⡄⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⡟⢠⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡫⠞⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⢤⡙⢿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⠀⣿⡀⠀⠀⠀⠀
⠀⠀⢀⡴⠞⣡⣬⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⠻⠥⠯⢤⣄⣀⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣀⣤⠤⠽⠦⠭⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⡞⢿⣦⡀⠀⠀
⠀⣰⠏⣡⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣧⡀⠀⠀⠀⠀⠉⠙⠲⢤⠀⠀⠀⠀⠤⠒⠋⠉⠁⠀⠀⠀⠀⠀⣴⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⣜⠛⣆⠀
⠼⠁⣴⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣆⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡆⠘⢆
*/

/*
 * Runs ONCE. Converts the NHPN highway JSON (I90_WA_ID.json) into a plain CSV
 * lookup file indexed by geohash. No database is allowed.
 *
 *   JSON Reader -> Segment Filter -> Segment Splitter -> Geohash Indexer -> Lookup File Writer
 *
 * Output CSV (one row per highway point, sorted by geohash, FR-03 / FR-04):
 *     GEOHASH,ROUTE,STFIPS,BEGMP,ENDMP,LAT,LON,CHANNEL
 *     c2kz3,I90,16,0.0,6.825,47.709889,-116.993985,10
 *
 * Facts about the provided JSON (checked):
 *   - Top level is {"type":"FeatureCollection","features":[ ... ]}
 *   - 1275 features: 1148 Washington (STFIPS "53") and 127 Idaho (STFIPS "16")
 *   - Every geometry is a "LineString"
 *   - Each feature has exactly: geometry, SIGN1, STFIPS, BEGMP, ENDMP
 *   - IMPORTANT: coordinates are [LONGITUDE, LATITUDE] (longitude first!)
 *
 * Usage: java Hi_Chan.Preprocessor I90_WA_ID.json segments.csv
 */
public class Preprocessor {

    public static class RoadFeature {
        public String route;                 // SIGN1, e.g. "I90"
        public String stfips;                // "53" or "16"
        public double begMp;                 // BEGMP
        public double endMp;                 // ENDMP
        public List<double[]> coordinates;   // each double[] is {lon, lat}, same order as the file

        // Constructor / Stores all the road data
        public RoadFeature(String route, String stfips, double begMp, double endMp, List<double[]> coordinates) {

            // Routes / States
            this.route = route;
            this.stfips = stfips;

            // Coordinates (makes sure there's no mean nullPointers :/ )
            this.coordinates = (coordinates == null) ? new ArrayList<>() : coordinates;

            // Points
            this.begMp = begMp;
            this.endMp = endMp;
        } // end of RoadFeature

        // Parses output / Example: (I90 16 MP 0.0-6.825)
        @Override
        public String toString() {
            int count = (coordinates == null) ? 0 : coordinates.size();
            return route + " " + stfips + " MP " + begMp + "-" + endMp + " (" + count + " points)";

        } // end of toString
    } // end of RoadFeature

    // Gets the JSON / Parses it / Sends it to output
    // Any problem prints one line, ERROR:<file>:<reason>, and exits with status 1 (NFR-U1, NFR-U2)
    public static void main(String[] args) {
        if(args.length == 2) {
            // Get JSON
            String json;
            try {
                json = readJsonFile(args[0]);
            } catch (NoSuchFileException e) {
                System.err.println("ERROR:" + args[0] + ":file not found");
                System.exit(1);
                return;
            } catch (IOException e) {
                System.err.println("ERROR:" + args[0] + ":file not readable");
                System.exit(1);
                return;
            } // end of try catch

            if (json.trim().isEmpty()) {
                System.err.println("ERROR:" + args[0] + ":file empty");
                System.exit(1);
                return;
            } // end of if

            // Parse
            List<RoadFeature> features;
            try {
                features = parseFeatures(json);
            } catch (RuntimeException e) {
                // oopsie was made while parsing (bad number, missing field, etc.)
                System.err.println("ERROR:" + args[0] + ":bad value in JSON");
                System.exit(1);
                return;
            } // end of try catch

            // Filter, split at 10-mile marks, geohash, sort
            List<RoadFeature> kept = filterSegments(features);
            List<HighwaySegment> rows = splitAtChannelBoundaries(kept);
            computeGeohashes(rows, GeoHash.DEFAULT_PRECISION);
            rows = buildSegments(rows);

            // Output
            try {
                writeCsv(rows, args[1]);
            } catch (IOException e) {
                System.err.println("ERROR:" + args[1] + ":cannot write file");
                System.exit(1);
                return;
            } // end of try catch

            System.out.println("Read " + features.size() +
                    " features, wrote " + rows.size() + " rows"); // verify it worked via the print

        } else {
            // Wrong number of arguments so say how to run it and exit with an error code
            System.err.println("Usage: java Hi_Chan.Preprocessor <input.json> <output.csv>");
            System.exit(1);

        } // end of if
    } // end of main

    // Reads the entire JSON file and turns it into one string
    public static String readJsonFile(String path) throws IOException {

        // Bob the Builder String
        StringBuilder sb = new StringBuilder();

        // Reads the file path (as UTF-8) and appends each line and creates a new line
        // for each until line is null
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            String line;

            while ((line = br.readLine()) != null)
                sb.append(line).append("\n");

        } // end of try

        return sb.toString();
    } // end of readJsonFile

    // Breaks down each feature into smaller substrings called chunks
    // Takes all those chunks at the end and returns them as a fully parsed array called features
    // takes in JSON string and outputs I90 16 MP 0.0-6.825
    public static List<RoadFeature> parseFeatures(String json) {

        List<RoadFeature> features = new ArrayList<>();

        // returns index of the geometry and returns -1 if not found
        int start = json.indexOf("\"geometry\"");

        // matches one number where -? is 0 or 1,
        // d+ any digit one or more,
        // .? is a decimal point,
        // d* is zero or more,
        // and the last part lets scientific notation like 1.2E-5 through
        String number = "-?\\d+\\.?\\d*(?:[eE][-+]?\\d+)?";

        // matches one inner [lon, lat] pair, an extra third value (like elevation) is allowed but ignored
        // group 1 is longitude and group 2 is latitude
        Pattern pairPattern = Pattern.compile("\\[\\s*(" + number + ")\\s*,\\s*(" + number + ")(?:\\s*,\\s*" + number + ")*\\s*\\]");

        // while we still have geometry
        while (start != -1) {

            // Geometry
            // get next Geometry
            int next = json.indexOf("\"geometry\"", start  +1);

            // gives the text from start up to next and nothing more
            String chunk = (next == -1) ? json.substring(start) : json.substring(start, next);

            // Coordinates
            // breaks the coordinates into a substring chunk
            // (empty if the feature has no coordinates, like "geometry": null)
            String coordText = "";
            int coordKey = chunk.indexOf("\"coordinates\"");
            int open = (coordKey == -1) ? -1 : chunk.indexOf("[", coordKey);

            if (open != -1) {
                int depth = 0; // how many brackets deep we are
                int close = chunk.length();

                // walks forward until the bracket that opened the coordinates is closed again
                for (int i = open; i < chunk.length(); i++) {
                    char c = chunk.charAt(i);

                    if (c == '[') {
                        depth++;

                    } else if (c == ']') {
                        depth--;

                        if (depth == 0) {
                            close = i + 1;
                            break;
                        } // end of if
                    } // end of if
                } // end of for

                coordText = chunk.substring(open, close);
            } // end of if

            // cord list
            List<double[]> coords = new ArrayList<>();

            // takes previous pattern and checks for a match
            Matcher matcher = pairPattern.matcher(coordText);

            // while match is found
            while (matcher.find()) {
                double lon = Double.parseDouble(matcher.group(1)); // first is always longitude then adds to parse
                double lat = Double.parseDouble(matcher.group(2)); // the next number is always latitude
                coords.add(new double[]{ lon, lat }); // adds both to array list

            } // end of while

            // Last Fields
            String route  = getValue(chunk, "SIGN1");
            String stfips = getValue(chunk, "STFIPS");
            String begText = getValue(chunk, "BEGMP");
            String endText = getValue(chunk, "ENDMP");

            // only keep the feature if every field was actually there (skips broken or null ones)
            if (route != null && stfips != null && begText != null && endText != null) {
                double begMp  = Double.parseDouble(begText);
                double endMp  = Double.parseDouble(endText);

                // creates the given feature one at a time and hands them to the array for storage
                features.add(new RoadFeature(route, stfips, begMp, endMp, coords));

            } // end of if

            start = next;
        } // end of while

        return features; // yipee
    } // end of parseFeatures


    // Keeps only features on I-90 in Washingto nor Idaho and prints what we skip
    public static List<RoadFeature> filterSegments(List<RoadFeature> features) {
        List<RoadFeature> kept = new ArrayList<>(); // create new list

        for (RoadFeature feature : features) { // for each feature
            // if its in the states we want
            if(feature.route == HiChanConfig.HIGHWAY && feature.stfips == HiChanConfig.STATE_WA || feature.stfips == HiChanConfig.STATE_ID)
                kept.add(feature); // add the feature
            else
                System.out.println("Skipped: "  + feature); // what was skipped

        } // end of for

        return kept; // yipee
    } // end of filterSegments


    public static List<HighwaySegment> splitAtChannelBoundaries(List<RoadFeature> features) {


        List<HighwaySegment> rows = new ArrayList<>(); // new rows list

        // LinkedHashMap keeps the groups in file order
        // groups the pieces of each NHPN segment
        Map<String, List<RoadFeature>> groups = new LinkedHashMap<>();// creating a new group

        for (RoadFeature f : features) { // for each road feature
            String key = f.route + "|" + f.stfips + "|" + f.begMp + "|" + f.endMp; // string together the features
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f); // add to f
        } // end of for

        for (List<RoadFeature> group : groups.values()) { // for each group
            RoadFeature first = group.get(0); // every piece shares route, state and mileposts

            // turn each piece west to east, sort the pieces west to east, then join them
            List<List<double[]>> pieces = new ArrayList<>(); // new pieces list

            for (RoadFeature f : group) { // for each group of roadfeatures
                if (f.coordinates.isEmpty())
                    continue; // nothing to add from an empty piece

                List<double[]> piece = new ArrayList<>(f.coordinates); // new piece of cordinates

                if (piece.get(0)[0] > piece.get(piece.size() - 1)[0]) // first lon east of last lon
                    Collections.reverse(piece);
                pieces.add(piece); //add them all at the end
            } // end of for

            pieces.sort(Comparator.comparingDouble(piece -> piece.get(0)[0])); // westernmost piece first

            List<double[]> points = new ArrayList<>(); // new points list

            for (List<double[]> piece : pieces) // for each piece
                points.addAll(piece); // add them

            if (points.isEmpty()) // if its empty then skip
                continue; // group had no coordinates at all

            // miles from the first point to each point across all the pieces
            double[] distance = new double[points.size()]; // distnace in points

            for (int i = 1; i < points.size(); i++) { // for each point
                double[] a = points.get(i - 1);
                double[] b = points.get(i);
                // points are {lon, lat}, haversineMiles wants (lat, lon, lat, lon)
                distance[i] = distance[i - 1] + GeoHash.haversineMiles(a[1], a[0], b[1], b[0]);
            } // end of for

            // every 10-mile mark strictly between BEGMP and ENDMP, and the point closest to it
            int step = HiChanConfig.CHANNEL_MILES;
            List<Integer> marks = new ArrayList<>(); // new marks list
            List<Integer> splitAt = new ArrayList<>(); // index of the last point in each mark's channel
            // love mark

            for (int mark = ((int) Math.floor(first.begMp / step) + 1) * step; mark < first.endMp; mark += step) {
                double target = mark - first.begMp; // miles from the first point to this mark

                int best = 0;
                for (int i = 1; i < distance.length; i++) { // for each distance
                    if (Math.abs(distance[i] - target) < Math.abs(distance[best] - target))
                        best = i;
                } // end of for

                // a later mark can never split before an earlier one
                if (!splitAt.isEmpty() && best < splitAt.get(splitAt.size() - 1))
                    best = splitAt.get(splitAt.size() - 1);

                marks.add(mark);
                splitAt.add(best);
                // gotta love mark
            } // end of for

            // points past the last split get the channel at or above ENDMP
            int lastChannel = HiChan.computeChannelNumber(first.endMp);

            // one row per point, west to east, geohash left null for computeGeohashes
            for (int i = 0; i < points.size(); i++) {
                int channel = lastChannel;

                for (int m = 0; m < marks.size(); m++) {
                    if (i <= splitAt.get(m)) { // up to and including the split point
                        channel = marks.get(m);
                        break;
                    } // end of if
                } // end of for

                double[] p = points.get(i);
                rows.add(new HighwaySegment(null, first.route, first.stfips, first.begMp, first.endMp,
                        p[1], p[0], channel)); // lat = p[1], lon = p[0]
            } // end of for
        } // end of for

        return rows; // yipee
    } // end of splitAtChannelBoundaries

    // Geohash Indexer: sets each row's geohash from its own LAT and LON (latitude first)
    public static void computeGeohashes(List<HighwaySegment> rows, int precision) {

        // adding each point's geohash
        for(HighwaySegment row : rows)
            row.setGeohash(GeoHash.encode(row.getLat(), row.getLon(), precision));

    } // end of computeGeohashes

    // returns the rows sorted by geohash (FR-04)
    public static List<HighwaySegment> buildSegments(List<HighwaySegment> rows) {
        List<HighwaySegment> sorted = new ArrayList<>(rows);

        sorted.sort(Comparator.comparing(HighwaySegment::getGeohash).thenComparingDouble(HighwaySegment::getBegMp));
        // comparator that sorts alphabetically by geohash string using method reference, then when two rows have same geohash, the smaller BegMP goes first.

        return sorted;
    } // end of buildSegments

    // outputs the CSV with a header and with the rows appended after
    public static void writeCsv(List<HighwaySegment> rows, String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {

            // Header
            pw.println("GEOHASH,ROUTE,STFIPS,BEGMP,ENDMP,LAT,LON,CHANNEL");

            // appends to rows from toCsvLine
            for(HighwaySegment row : rows)
                pw.println(row.toCsvLine());

        } // end of try
    } // end of writeCsv

    // finds the value for a key in a chunk, returns null if the key isn't there
    public static String getValue(String chunk, String key){

        // where the key is and the colon right after it
        int keyPos = chunk.indexOf("\"" + key + "\"");

        // key isn't in this chunk so there is nothing to read
        if (keyPos == -1)
            return null;

        int colon = chunk.indexOf(":", keyPos);

        // no colon means the key has no value
        if (colon == -1)
            return null;

        // the value ends at the next comma, closing brace or the end of the line
        int comma = chunk.indexOf(",", colon);
        int brace = chunk.indexOf("}", colon);
        int newline = chunk.indexOf("\n", colon);

        // finds the smallest of the three that actually exist
        int end = -1;

        if (comma != -1)
            end = comma;

        if (brace != -1 && (end == -1 || brace < end))
            end = brace;

        if (newline != -1 && (end == -1 || newline < end))
            end = newline;

        // If none were found the value runs to the end of the chunk
        if (end == -1)
            end = chunk.length();

        // cuts it out strip spaces/\r and remove quotes
        String value = chunk.substring(colon + 1, end).trim().replace("\"", "");

        // an empty or null value counts as missing
        if (value.isEmpty() || value.equals("null"))
            return null;

        return value;

    } // end of getValue

} // end of Hi_Chan.Preprocessor
