package org.dbpedia.events.wikipedia;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: Dimitris Kontokostas
 * Description
 * Created: 5/30/14 11:50 AM
 */

public class WikipediaRevisionResolver {
    private final CandidateItem candidate;
    private final List<Revision> revisions;

    final int maxRevisionsToFetch = 500;
    final int minutesBeforeCandidate = -720;
    final int minutesAfterCandidate  = 720;

    public WikipediaRevisionResolver(CandidateItem candidate) {
        this.candidate = candidate;
        this.revisions = new ArrayList<Revision>();
        initRevisions();
    }

    public long getRevisionBefore() {
        // we already have this as the last item of the revision list
        int revisionSize = revisions.size();
        if (revisionSize > 0)
            // Send the parent revision of the last item
            return revisions.get(revisionSize-1).getParentRevisionId();
        else
            return 0;
    }

    public long getRevisionAfter() {
        // we already have this as the first item of the revision list
        if (revisions.size()>0)
            return revisions.get(0).getRevisionId();
        else
            return 0;
    }

    private void initRevisions() {
        // Generate a time after <minutesAfterCandidate> the events was fired
        Date timeStart = addMinutes(candidate.getDateTimeFired(), minutesBeforeCandidate);
        Date timeEnd = addMinutes(candidate.getDateTimeFired(), minutesAfterCandidate);

        SimpleDateFormat revisionDateFormatter = new SimpleDateFormat(
                WikipediaUtils.getWikipediaTimestampFormatURL(), Locale.ENGLISH );
        revisionDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formattedTimestampStart = revisionDateFormatter.format(timeStart);
        String formattedTimestampEnd = revisionDateFormatter.format(timeEnd);

        String wikipediaApi = WikipediaUtils.getWikipediaAPI(candidate.getLanguage());
        revisions.addAll(
                WikipediaUtils.getRevisions(
                        wikipediaApi, candidate.getTitleDecoded(), formattedTimestampStart, formattedTimestampEnd, maxRevisionsToFetch)
        );

        // Sort latest date first
        Collections.sort(revisions, Collections.reverseOrder());
    }

    //minus number would decrement the days
    private Date addMinutes(Date time, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }
}
