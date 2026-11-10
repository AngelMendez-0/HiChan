package Hi_Chan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * Converts a latitude/longitude pair into a geohash string.
 * Both the Hi_Chan.Preprocessor (for each road coordinate) and Hi_Chan.HiChan (for each GPS
 * point) use this class, so the two programs MUST produce identical hashes
 * for the same location.
 *
 * WHAT A GEOHASH IS:
 *   The world is cut into a grid. Each geohash names one rectangular cell of that grid,
 *   and the longer the hash, the smaller the cell. At precision 5 a cell is roughly
 *   3 miles across. Two points in the same cell get the same hash, so we can use the
 *   hash as a HashMap key to find "highway points near this GPS reading" quickly
 *   instead of comparing the reading against every point on I-90.
 *
 * Project spec: precision 5 (HiChanConfig.GEOHASH_PRECISION, AD-2).
 */
public class GeoHash {

    // The geohash Base32 alphabet. Note it skips the letters a, i, l, and o.
    // Index 0 = '0', index 31 = 'z'. Each character stands for 5 bits (2^5 = 32).
    public static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    // Default precision (number of characters) used everywhere in this project.
    public static final int DEFAULT_PRECISION = HiChanConfig.GEOHASH_PRECISION;

    // Earth radius in miles, used by the Haversine formula
    private static final double EARTH_RADIUS_MILES = 3958.8;

    /*
     * Returns a geohash string that is `precision` characters long.
     *
     * How it works: repeatedly cut the world in half and record which half the point is in.
     *   - Start with the whole world: latitude [-90, 90], longitude [-180, 180].
     *   - Each step halves ONE range and records a bit: 1 if the point is in the upper
     *     half, 0 if in the lower half.
     *   - Steps alternate between longitude and latitude (longitude first).
     *   - Every 5 bits are packed into one Base32 character.
     * Example: encode(47.709889, -116.993985, 5) returns "c2kz3".
     */
    public static String encode(double lat, double lon, int precision) {
        // start with the whole world as the range for each axis.
        // Every step below shrinks one of these ranges by half.
        double minLat = -90, maxLat = 90;
        double minLon = -180, maxLon = 180;

        // set up the result and the three trackers
        StringBuilder result = new StringBuilder(); // the geohash being built, one character at a time
        boolean evenBit = true; // true means the next bit is longitude, false means latitude
        int bit = 0;            // how many bits are in the current character (0-4)
        int ch = 0;             // the 5-bit value being built up

        // keep adding bits until we have enough characters
        while (result.length() < precision) {

            // pick the axis for this bit: longitude on "even" turns, latitude on the others
            if (evenBit) {
                // find the middle of the current longitude range
                double mid = (minLon + maxLon) / 2;
                // which half is the point in?
                if (lon >= mid) {
                    ch = (ch << 1) | 1; // east half: shift the bits left and add a 1
                    minLon = mid;       // the range is now just the upper (east) half
                } else {
                    ch = ch << 1;       // west half: shift the bits left and add a 0
                    maxLon = mid;       // the range is now just the lower (west) half
                }
            } else {
                // same three steps, but for latitude (north half = 1, south half = 0)
                double mid = (minLat + maxLat) / 2;
                if (lat >= mid) {
                    ch = (ch << 1) | 1;
                    minLat = mid;
                } else {
                    ch = ch << 1;
                    maxLat = mid;
                }
            }

            // switch axis for the next bit and count this bit
            evenBit = !evenBit;
            bit++;

            // once we have 5 bits, they form a number 0-31: look up its Base32 character
            if (bit == 5) {
                result.append(BASE32.charAt(ch));
                bit = 0; // start collecting the next character
                ch = 0;
            }
        }

        // hand back the finished geohash
        return result.toString();
    } // end of encode

    // Convenience overload that uses DEFAULT_PRECISION (5) so callers don't repeat it
    public static String encode(double lat, double lon) {
        // just call the 3-argument version with the project's standard precision
        return encode(lat, lon, DEFAULT_PRECISION);
    } // end of encode

    /*
     * Returns the (up to) 8 geohashes surrounding the given cell; fewer at a pole.
     * Used only as a fallback when a GPS reading's own cell has no highway point,
     * which can happen when the reading sits near a cell edge (AD-2).
     *
     * How it works:
     *   1. DECODE: run the encode steps backwards. Each character's 5 bits tell us which
     *      half to keep for longitude/latitude, which gives back the cell's edges.
     *   2. From the edges get the cell's height, width and center point.
     *   3. ENCODE the center shifted by one cell height/width in each of the 8 directions.
     *      A point one full cell away is guaranteed to land in the neighboring cell.
     */
    public static List<String> neighbors(String geohash) {
        // DECODE. Start with the whole world again, exactly like encode does.
        double minLat = -90, maxLat = 90;
        double minLon = -180, maxLon = 180;
        boolean evenBit = true; // same longitude-first order that encode uses

        // go through each character of the hash
        for (int i = 0; i < geohash.length(); i++) {
            // turn the character back into its number 0-31 using its position in BASE32
            int value = BASE32.indexOf(geohash.charAt(i));

            // read that number's 5 bits from the highest to the lowest
            for (int b = 4; b >= 0; b--) {
                int bit = (value >> b) & 1; // pull out bit number b (0 or 1)

                // use the bit to pick the same half that encode picked
                if (evenBit) {
                    double mid = (minLon + maxLon) / 2;
                    if (bit == 1) minLon = mid; else maxLon = mid; // 1 = keep upper half, 0 = keep lower half
                } else {
                    double mid = (minLat + maxLat) / 2;
                    if (bit == 1) minLat = mid; else maxLat = mid;
                }
                evenBit = !evenBit; // the next bit belongs to the other axis
            }
        }
        // the four edges now describe the cell this hash names

        // measure the cell and find its center
        double cellHeight = maxLat - minLat; // how far the cell spans north-south, in degrees
        double cellWidth = maxLon - minLon;  // how far the cell spans east-west, in degrees
        double centerLat = (minLat + maxLat) / 2;
        double centerLon = (minLon + maxLon) / 2;

        // visit the 8 surrounding cells.
        // A LinkedHashSet keeps the order we found them in and refuses repeats.
        Set<String> found = new LinkedHashSet<>();
        for (int dLat = -1; dLat <= 1; dLat++) {         // -1 = south, 0 = same row, 1 = north
            for (int dLon = -1; dLon <= 1; dLon++) {     // -1 = west, 0 = same column, 1 = east
                if (dLat == 0 && dLon == 0)
                    continue; // that's the cell itself, not a neighbor

                // move one whole cell from the center in this direction;
                // that point is guaranteed to land inside the neighboring cell
                double lat = centerLat + dLat * cellHeight;
                double lon = centerLon + dLon * cellWidth;

                if (lat > 90 || lat < -90)
                    continue; // past a pole: there is no cell there, so skip it

                // longitude wraps around the world: just past 180 is just past -180
                if (lon > 180) lon -= 360;
                if (lon < -180) lon += 360;

                // encode the shifted point at the same precision to get the neighbor's hash
                String hash = encode(lat, lon, geohash.length());

                // near a pole or a wrap, a shift can land back in the original cell: ignore that
                if (!hash.equals(geohash))
                    found.add(hash);
            }
        }

        // return the neighbors as a List
        return new ArrayList<>(found);
    } // end of neighbors

    /*
     * Haversine distance in miles between two latitude/longitude points.
     * Used to find the nearest highway point to a GPS reading (FR-06).
     *
     * The Earth is (nearly) a sphere, so the straight-line distance on a flat map would be
     * wrong. The Haversine formula gives the great-circle distance along the surface.
     */
    public static double haversineMiles(double lat1, double lon1, double lat2, double lon2) {
        // how far apart the points are in each direction.
        // Java's trig functions want radians, not degrees, so convert.
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        // 'a' is the Haversine term: sin^2(dLat/2) + cos(lat1) * cos(lat2) * sin^2(dLon/2).
        // The cos terms shrink the east-west part, because lines of longitude get closer
        // together as you move away from the equator.
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        // turn 'a' into an arc length along the Earth's surface:
        // 2 * radius * asin(sqrt(a)), with the radius in miles so the answer is in miles
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.sqrt(a));
    } // end of haversineMiles
} //end of Hi_Chan.GeoHash
