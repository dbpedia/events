package org.dbpedia.events.wikipedia;

import java.util.Date;

/**
 * User: Dimitris Kontokostas
 * A revision placeholder
 * Created: 5/30/14 1:05 PM
 */
class Revision implements Comparable<Revision>{
    private final long revisionId;
    private final long parentRevisionId;
    private final Date timestamp;

    public Revision(long revisionId, long parentRevisionId, Date timestamp) {
        this.revisionId = revisionId;
        this.parentRevisionId = parentRevisionId;
        this.timestamp = timestamp;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public long getParentRevisionId() {
        return parentRevisionId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(Revision revision) {
        return this.getTimestamp().compareTo(revision.getTimestamp());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Revision) {
            return this.compareTo((Revision) obj) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        return timestamp.toString() + " / " + revisionId;
    }


}
