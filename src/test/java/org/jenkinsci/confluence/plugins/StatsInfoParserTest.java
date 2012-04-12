package org.jenkinsci.confluence.plugins;

import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import com.atlassian.confluence.json.parser.JSONException;

import junit.framework.TestCase;

public class StatsInfoParserTest extends TestCase {

    public void testZeroAndNoneTimeseries() throws Exception {
        final String url = getChartUrl("/zero-timeseries.stats.json");
        final String url2 = getChartUrl("/none-timeseries.stats.json");
        assertEquals("none timeseries and 0 timeseries must result in the same url", url, url2);
    }

    public void testNoJson() throws Exception {
        try {
            final String url = new StatsInfoParser("my-plugin", null).renderChartUrl(false);
            fail("'null' json must throw an exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testIvalidJson() throws Exception {
        try {
            new StatsInfoParser("my-plugin", "no-json").renderChartUrl(false);
            fail("invalid json must throw an exception");
        } catch (JSONException e) {
            // expected
        }
    }

    public void testFallbackName() throws Exception {
        final String url = new StatsInfoParser("", "{}").renderChartUrl(true);
        assertTrue("blank name must be replaced by 'unknown", url.contains("unknown"));
    }

    public void testOneTimeseries() throws Exception {
        final String url = getChartUrl("/one-timeseries.stats.json");
        System.out.println(url);
    }

    public void testTwoTimeseries() throws Exception {
        final String url = getChartUrl("/two-timeseries.stats.json");
        System.out.println(url);
    }

    public void testCommonNumbers() throws Exception {
        final String url = getChartUrl("/plugin-name.stats.json");
        System.out.println(url);
    }

    public void testSubversionPlugin() throws Exception {
        final String url = getChartUrl("/subversion.stats.json");
        System.out.println(url);
    }

    public void testCrap4jPlugin() throws Exception {
        final String url = getChartUrl("/crap4j.stats.json");
        System.out.println(url);
    }

    private String getChartUrl(String statsJsonResource) throws Exception {
        final InputStream stream = StatsInfoParserTest.class.getResourceAsStream(statsJsonResource);
        final String json = IOUtils.toString(stream);
        final String url = new StatsInfoParser("my-plugin", json).renderChartUrl(false);
        assertNotNull("url is null", url);
        assertTrue("url must start with http", url.startsWith("http"));
        assertTrue("url must contain plugin name", url.contains("my-plugin"));
        return url;
    }

    public void testname() throws Exception {
        final InputStream stream = StatsInfoParserTest.class.getResourceAsStream("/plugin-name.stats.json");
        final String json = IOUtils.toString(stream);
        StringBuilder toBeRendered = new StringBuilder();
        StatsInfoParser parser = new StatsInfoParser("my-plugin2", json);
        final String chartUrl = parser.renderChartUrl(true);
        final SortedMap<Date, Integer> sortedSeries = parser.getSortedSeries();

        SimpleDateFormat df = new SimpleDateFormat("yyyy.MM");
        toBeRendered.append("\\ || Stats | ");
        int half = sortedSeries.size() / 2, i = 0;
        for (Entry<Date, Integer> serie : sortedSeries.entrySet()) {
            toBeRendered.append(df.format(serie.getKey())).append(": *").append(serie.getValue().toString()).append("*, ");
            i++;
            if(i == half){
                toBeRendered.append("\\\\");    
            }
        }
        toBeRendered.append(" | !").append(chartUrl).append("! | ");

        System.out.println(toBeRendered.toString());
    }
}
