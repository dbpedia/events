package org.dbpedia.events.wikipedia;

import org.dbpedia.events.utils.DateUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * User: Dimitris Kontokostas
 * Description
 * Created: 5/30/14 11:52 AM
 */
public class WikipediaUtils {

    public static String getWikipediaAPI(String languageIsoCode) {
        return "http://"+ languageIsoCode + ".wikipedia.org/w/api.php";
    }

    /* Date format for timestamp use on API urls */
    public static String getWikipediaTimestampFormatURL() {
        return "yyyyMMddHHmmss";
    }

    /* Date format for timestam XML results */
    public static String getWikipediaTimestampFormatXML() {
        return "yyyy-MM-dd'T'HH:mm:ss'Z'";
    }


    public static List<Revision> getRevisions(String api, String title, String timestampStart, String timestampEnd, int maxRevisions) {
        String revisionUrl = api + "?action=query&prop=revisions&titles=" + title +  "&rvprop=timestamp|ids&rvstart=" + timestampStart + "&rvend=" + timestampEnd + "&rvlimit=" + maxRevisions + "&format=xml";
        List<Revision> revisions = new ArrayList<Revision>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        Document doc = null;
        try {
            db = dbf.newDocumentBuilder();
            doc = db.parse(new URL(revisionUrl).openStream());
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return revisions;
        } catch (SAXException e) {
            e.printStackTrace();
            return revisions;
        } catch (IOException e) {
            e.printStackTrace();
            return revisions;
        }

        NodeList nodeList = doc.getElementsByTagName("rev");
        for (int i = 0; i < nodeList.getLength(); i++) {
            NamedNodeMap attributes = nodeList.item(i).getAttributes();

            //Get revision id
            String revisionIdStr = attributes.getNamedItem("revid").getNodeValue();
            String parrentRevIdStr = attributes.getNamedItem("parentid").getNodeValue();
            String revisionTimestampStr = attributes.getNamedItem("timestamp").getNodeValue();

            long revisionId =  Long.parseLong(revisionIdStr);
            long parrentRevId = Long.parseLong(parrentRevIdStr);

            Date revisionTimestamp = null;
            try {
                revisionTimestamp = DateUtils.getDateFromString(revisionTimestampStr, getWikipediaTimestampFormatXML());
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (revisionTimestamp != null)
                revisions.add(new Revision(revisionId, parrentRevId, revisionTimestamp));

        }



        return revisions;
    }




}
