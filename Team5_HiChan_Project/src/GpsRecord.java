package Hi_Chan;

/*
 * One line from a GPS Logger file, for example:
 *
 *     date time,latitude,longitude,altitude(m),speed(m/s),sat_used
 *     2026-09-22 23:07:25.000,47.65384674,-117.3701775,565.534,15.03,25
 *
 * Hi_Chan.HiChan fills in the extra fields (geohash, matched segment, channelId)
 * while it processes the log.
 *
 * A GpsRecord is also used for BAD lines: if a line can't be parsed, HiChan keeps it in
 * the list with errorText set, so the output file has the same lines in the same order.
 */
public class GpsRecord {

    // Keep the original line exactly so the output file can copy it and
    // append the Channel ID as the last field (that's what the spec asks for).
    private String originalLine;

    // Important Data: the six values parsed from the line
    private String dateTime;
    private double latitude;
    private double longitude;
    private double altitude;
    private double speed;
    private int satUsed;

    // INIT: these start out null and are filled in later by HiChan
    private String geohash;            // precision-5 geohash of this point
    private HighwaySegment segment;    // nearest highway point, or null if no match
    private String channelId;          // e.g. "#I90WAEB_290" or "#I90WA??_290", null until built
    private String errorText;          // why the line was skipped (FR-14), or null


    // Constructor: stores the parsed values and the original text line.
    // geohash, segment, channelId and errorText are left null on purpose;
    // HiChan sets them later as it works through the log.
    public GpsRecord(String originalLine, String dateTime, double latitude, double longitude,
                     double altitude, double speed, int satUsed) {
        // copy each parameter into the matching field (this.x is the field, x is the parameter)
        this.originalLine = originalLine; // the raw text, kept for the output file
        this.dateTime = dateTime;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.satUsed = satUsed;

        // geohash, segment, channelId and errorText are not assigned here,
        // so Java leaves them null until HiChan sets them
    } // end of Hi_Chan.GpsRecord

    /*
     * Parses one data line of the GPS log into a GpsRecord.
     * (The caller, HiChan.readGpsLog, skips the header line, so this only gets data lines.)
     *
     * If anything is wrong with the line, it throws IllegalArgumentException with a short
     * reason that has NO commas. readGpsLog catches it and writes that reason after
     * SKIPPED-Error: in the output (FR-14). The reason can't contain a comma because that
     * would add an extra field to the output CSV line.
     */
    public static GpsRecord fromCsvLine(String line) {
        // a null line can't be split, so say so clearly instead of crashing
        if (line == null)
            throw new IllegalArgumentException("line is null");

        // drop a trailing "\r" so Windows (CRLF) and Unix (LF) files both work (NFR-D1)
        if (line.endsWith("\r"))
            line = line.substring(0, line.length() - 1);

        // split into fields. The -1 keeps empty trailing fields,
        // so a line ending in a comma is counted as having an extra empty field.
        String[] parts = line.split(",", -1);

        // a good line has exactly 6 fields: date time, latitude, longitude,
        // altitude, speed, sat_used. Any other count is a bad line.
        if (parts.length != 6)
            throw new IllegalArgumentException("expected 6 fields but found " + parts.length);

        // field 0, the timestamp. It stays as text, but it can't be blank.
        String dateTime = parts[0].trim();
        if (dateTime.isEmpty())
            throw new IllegalArgumentException("date time is empty");

        // fields 1-4 are decimal numbers; parseNumber trims them and
        // throws a short reason naming the field if one isn't a number
        double latitude = parseNumber(parts[1], "latitude");
        double longitude = parseNumber(parts[2], "longitude");
        double altitude = parseNumber(parts[3], "altitude");
        double speed = parseNumber(parts[4], "speed");

        // field 5 must be a WHOLE number, so a value like "2.5" is rejected
        int satUsed;
        try {
            satUsed = Integer.parseInt(parts[5].trim());
        } catch (NumberFormatException e) {
            // we write our own message because Java's message may contain characters we don't want
            throw new IllegalArgumentException("sat_used is not a whole number");
        }

        // the coordinates must be real places on Earth
        if (latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("latitude out of range");
        if (longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("longitude out of range");

        // everything checked out. Build the record, keeping the line
        // (without the \r) so the output can copy it exactly.
        return new GpsRecord(line, dateTime, latitude, longitude, altitude, speed, satUsed);
    } // end of fromCsvLine

    // Helper for fromCsvLine: parses a number or throws a short reason naming the field.
    // Double.parseDouble also accepts "NaN" and "Infinity", which are not real
    // coordinates or readings, so those are rejected too.
    private static double parseNumber(String text, String fieldName) {
        // try to read the text as a number (trim first so " 47.5 " works)
        double value;
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            // empty text, letters, etc.: report which field was wrong
            throw new IllegalArgumentException(fieldName + " is not a number");
        }

        // "NaN" and "Infinity" parse successfully but aren't real values, so reject them
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException(fieldName + " is not a number");

        // a good, finite number
        return value;
    } // end of parseNumber

    /*
     * Returns true if this point and `other` are practically the same spot, meaning the
     * truck isn't moving. The spec says to reuse the prior Channel ID in that case (FR-11).
     *
     * GPS "jitters" by a tiny amount even when parked, so == on doubles would almost never
     * match. Instead we allow a difference smaller than SAME_LOCATION_TOLERANCE (0.00001
     * degrees, about 1 meter) in both latitude and longitude.
     */
    public boolean isSameLocation(GpsRecord other) {
        // a missing record, or a skipped (bad) line on either side, is never "the same spot".
        // A bad line has no real coordinates to compare.
        if (other == null || errorText != null || other.errorText != null)
            return false;

        // compare latitude AND longitude with a tolerance instead of ==.
        // Both differences must be smaller than the tolerance for the points to match.
        return Math.abs(latitude - other.latitude) < HiChanConfig.SAME_LOCATION_TOLERANCE
                && Math.abs(longitude - other.longitude) < HiChanConfig.SAME_LOCATION_TOLERANCE;
    } // end of isSameLocation

    // the line written to the output file: the original line plus the Channel ID
    // as the last field (FR-12), or SKIPPED-Error:<reason> for a bad line (FR-14)
    public String toOutputLine() {
        // a bad line: keep its text and mark it as skipped, with the reason
        if (errorText != null)
            return originalLine + ",SKIPPED-Error:" + errorText;
        // a good line: original text + one more field; if no Channel ID was set, that field is empty
        return originalLine + "," + (channelId == null ? "" : channelId);
    } // end of toOutputLine

    // Getters / Setters
    public String getOriginalLine() { return originalLine; } // end of getOriginalLine

    public String getDateTime() { return dateTime; } // end of getDateTime

    public double getLatitude() { return latitude; } // end of getLatitude

    public double getLongitude() { return longitude; } // end of getLongitude

    public double getAltitude() { return altitude; } // end of getAltitude

    public double getSpeed() { return speed; } // end of getSpeed

    public int getSatUsed() { return satUsed; } // end of getSatUsed

    public String getGeohash() { return geohash; } // end of getGeohash

    public void setGeohash(String geohash) { this.geohash = geohash; } // end of setGeohash

    public HighwaySegment getSegment() { return segment; } // end of getSegment

    public void setSegment(HighwaySegment segment) { this.segment = segment; } // end of setSegment

    public String getChannelId() { return channelId; } // end of getChannelId

    public void setChannelId(String channelId) { this.channelId = channelId; } // end of setChannelId

    public String getErrorText() { return errorText; } // end of getErrorText

    public void setErrorText(String errorText) { this.errorText = errorText; } // end of setErrorText
} // end of Hi_Chan.GpsRecord
