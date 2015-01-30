package org.dbpedia.events.wikipedia;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;

/**
 * User: Dimitris Kontokostas
 * a twitter post that signals an event fires up
 * Created: 5/29/14 10:13 PM
 */
public class CandidateItem {
    private final String language;
    private final String title;
    private final Date dateTimeFired;

    public CandidateItem(String language, String title, Date dateTimeFired) {
        this.language = language;
        this.title = title;
        this.dateTimeFired = dateTimeFired;
    }

    public CandidateItem(URL wikipediaURL, Date dateTimeFired) {
        assert (wikipediaURL != null);
        assert (dateTimeFired != null);

        this.language = getWikiLang(wikipediaURL);
        this.title = getWikiTitle(wikipediaURL);
        this.dateTimeFired = dateTimeFired;
    }

    @Override
    public String toString() {
        return getDateTimeFired().toString() + " / " + getLanguage() + " / " + getTitle();
    }

    private String getWikiLang(URL wikipediaURL) {
        assert (wikipediaURL != null);

        return wikipediaURL.getHost().replace(".wikipedia.org", "");
    }

    private String getWikiTitle(URL wikipediaURL){
        assert (wikipediaURL != null);

        String wikiTitle = wikipediaURL.getPath().replace("/wiki/", "");
        try {
            wikiTitle = URLDecoder.decode(wikiTitle, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.err.println("Cannot decode url: " + wikipediaURL.toString());
        }
        return wikiTitle;
    }

    public String getLanguage() {
        return language;
    }

    public String getTitle() {
        return title;
    }

    public String getTitleDecoded() {
        try {
            return URLDecoder.decode(title, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return title;
    }

    public Date getDateTimeFired() {
        return dateTimeFired;
    }
}
