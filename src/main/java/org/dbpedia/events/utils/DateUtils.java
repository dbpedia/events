package org.dbpedia.events.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * User: Dimitris Kontokostas
 * Description
 * Created: 5/30/14 1:27 PM
 */
public class DateUtils {
    public static Date getDateFromString(String dateString, String format) throws ParseException {
        SimpleDateFormat formatter =
                new SimpleDateFormat(format, Locale.ENGLISH);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.parse(dateString);
    }

}
