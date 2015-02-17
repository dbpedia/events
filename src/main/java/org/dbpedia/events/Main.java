package org.dbpedia.events;

import org.dbpedia.events.utils.DateUtils;
import org.dbpedia.events.wikipedia.CandidateItem;
import org.dbpedia.events.wikipedia.WikipediaRevisionResolver;
import org.dbpedia.events.wikipedia.WikipediaUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: Dimitris Kontokostas
 * Description
 * Created: 5/29/14 10:05 PM
 */
public class Main {

    private static final String tweeterDateTimeFormat = "yyyy-MM-dd HH:mm:ss Z";

    public static void main(String[] args) throws Exception {

        File f = new File("/Users/magnus/Datasets/dbpedia-events/tweets.cut.tsv");

        List<CandidateItem> items = new ArrayList<CandidateItem>();

        try {
            BufferedReader r = new BufferedReader(new FileReader(f));

            String s;
            Set<String> supportedLanguages = new HashSet<String>(
                    Arrays.asList(
                            "en","de","fr","it","es","nl","pt","pl","ru","cs","ca","bn","hi","ja","zh","hu",
                            "ko","tr","ar","id","sr","sk","bg","sl","eu","eo","et","hr","el","be","cy","ur","ga"));
            while ((s = r.readLine()) != null) {
                String[] parts = s.split("\t");

                String wikipediaUrlStr = parts[1];
                URL wikipediaUrl = null;
                try {
                     wikipediaUrl = new URL(wikipediaUrlStr);
                } catch (MalformedURLException e) {
                    // Skip this iteration
                    System.err.println("Cannot parse URL: " + wikipediaUrlStr);
                    continue;
                }

                Date timestamp = null;
                String timestampStr = parts[0];
                try {
                    timestamp = DateUtils.getDateFromString(timestampStr, tweeterDateTimeFormat);
                } catch (ParseException e) {
                    // Skip this iteration
                    System.err.println("Cannot read date: " + timestampStr);
                    continue;
                }

                CandidateItem item = new CandidateItem(wikipediaUrl, timestamp);
                // For now try only the English language
                if (supportedLanguages.contains(item.getLanguage())) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
        }

        List<String> existingFilesList = getFileListForFolder(new File("results"));

        for (CandidateItem item : items) {


            // Timestamp string
            SimpleDateFormat revisionDateFormatter = new SimpleDateFormat(
                    WikipediaUtils.getWikipediaTimestampFormatURL(), Locale.ENGLISH );
            revisionDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateStr = revisionDateFormatter.format(item.getDateTimeFired());

            // Check to skip if file exists
            boolean skipProcessing = false;
            for (String filename: existingFilesList) {
                if (filename.startsWith(dateStr)) {
                    skipProcessing = true;
                    break;
                }
            }
            if (skipProcessing) {
                System.out.println("Skipping already existing filename: " + dateStr + "-" + item.getTitleDecoded());
                continue;
            }

            WikipediaRevisionResolver wrr = new WikipediaRevisionResolver(item);

            long revisionAfter = wrr.getRevisionAfter();
            long revisionBefore   = wrr.getRevisionBefore();

            tryAndWait1Sec();
            DBpediaItem itemAfter = new DBpediaItem(item.getLanguage(), item.getTitle(), revisionAfter);
            SortedSet<String> triplesAfterEdits = itemAfter.getTriplesCleaned();

            tryAndWait1Sec();
            DBpediaItem itemBefore   = new DBpediaItem(item.getLanguage(), item.getTitle(), revisionBefore);
            SortedSet<String> triplesBeforeEdits = itemBefore.getTriplesCleaned();

            // added triples
            SortedSet<String> added = new TreeSet();
            for (String current: triplesAfterEdits) {
                //add if previous does NOT contain current
                if ( ! triplesBeforeEdits.contains(current))
                    added.add(current);
            }

            // removed triples
            SortedSet<String> removed= new TreeSet();
            for (String current: triplesBeforeEdits) {
                //add if After does not contain current
                if (!triplesAfterEdits.contains(current))
                    removed.add(current);
            }

            // unmodified triples
            SortedSet<String> unmodified = new TreeSet();
            for (String current: triplesAfterEdits) {
                //add if previous does contain current
                if (triplesBeforeEdits.contains(current))
                    unmodified.add(current);
            }

            // Stats
            int itemsAdded = added.size(), itemsRemoved = removed.size(), itemsUnmodified = unmodified.size();




            String title = item.getTitleDecoded().replace("/","_").replace("\\", "_");
            String filename = "results/" + dateStr + "-" + item.getLanguage().toUpperCase() + "-A" + itemsAdded + "-D" + itemsRemoved + "-U" + itemsUnmodified + "-" + title + ".txt";


            try {
                File diffFile = new File (filename);
                if (diffFile.exists()) {
                    System.out.println("Skipping already existing filename: " + filename);
                    continue;
                }
                PrintWriter out = new PrintWriter(filename);

                out.println("#ADDED");
                for (String triple : added) {
                    out.println(triple);
                }

                out.println("#REMOVED");
                for (String triple : removed) {
                    out.println(triple);
                }
                out.println("#UNMODIFIED");
                for (String triple : unmodified) {
                    out.println(triple);
                }

                out.close();
                System.out.println("Wrote file: " + filename);
            } catch (Exception e) {
                System.out.println("Problem writing file: " + filename);
                e.printStackTrace();
            }

        }

    }

    public static List<String> getFileListForFolder(final File folder) {
        List<String> fileList = new ArrayList<String>();

        for (final File fileEntry : folder.listFiles()) {
            fileList.add(fileEntry.getName());
        }
        return fileList;
    }

    /*
    * Wikipedia has a 1 sec limit in consecutive calls
    * */
    private static void tryAndWait1Sec() {
        try {
            Thread.sleep(900);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
