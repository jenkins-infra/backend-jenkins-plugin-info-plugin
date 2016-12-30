package org.jenkinsci.confluence.plugins;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.atlassian.confluence.json.parser.JSONException;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class StatsInfoParser {

    private static final int NUMBER_OF_MONTHS = 12;

    // https://developers.google.com/chart/image/docs/chart_params
    private static final String QUERY_FORMAT = "" + // https://chart.googleapis.com/chart?
            // chart type: line chart
            "cht=lc" +
            // Axis labels
            "&chxl=1:|{1}|2:|Month" +
            // position of the 'Month' label
            "&chxp=2,50" +
            // Axis Range ( x | y )
            "&chxr=0,0,{3}|1,0," + NUMBER_OF_MONTHS +
            // Axis Style (axis#,color,size)
            "&chxs=1,676767,12" +
            // Visible Axes
            "&chxt=y,x,x" +
            // size of the chart
            "&chs=300x225" +
            // shown data range (min/max)
            "&chds=0,{3}" +
            // y axis - data (number of installations)
            "&chd=t:{2}" +
            // grid lines
            "&chg=9.09,-1,0,0" +
            // Line Style (thickness)
            "&chls=4" +
            // Line Color (series color) -> red line
            "&chco=d24939" +
            // Line Fill (below line) -> gray
            // "&chm=B,757172,0,0,0" +
            // chart title (plugin name)
            "&chtt={0}+-+installations";

    private final SortedMap<Date, Integer> sortedSeries = new TreeMap<Date, Integer>();
    private final String pluginName;
    private Integer maxNumber = 0;

    public StatsInfoParser(String pluginName, String statsJson) throws ParseException {
        if (StringUtils.isBlank(statsJson)) {
            throw new IllegalArgumentException("json  must not be blank/null");
        }
        this.pluginName = StringUtils.isBlank(pluginName) ? "unknown" : pluginName;
        init(statsJson);
    }

    /**
     * inits the state of the parser
     * 
     * @param statsJson
     *            the json continaing the installation timeseries to be parsed
     * @throws JSONException
     */
    private void init(String statsJson) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject stats = (JSONObject) parser.parse(statsJson);
        JSONObject timeseries = (JSONObject) stats.get("installations");

        SortedMap<Date, Integer> reverseSortedSeries = new TreeMap<Date, Integer>(Collections.reverseOrder());

        if (timeseries != null) {
            for (String monthStr : (Iterable<String>) timeseries.keySet()) {
                Date monthDate = new Date(Long.parseLong(monthStr));
                reverseSortedSeries.put(monthDate, ((Long) timeseries.get(monthStr)).intValue());
            }
        }

        int i = 0;
        for (Iterator<Entry<Date, Integer>> iterator = reverseSortedSeries.entrySet().iterator(); iterator.hasNext(); i++) {
            if (i >= NUMBER_OF_MONTHS) { // we only show numbers for the last 12 months
                break;
            }
            final Entry<Date, Integer> entry = iterator.next();
            sortedSeries.put(entry.getKey(), entry.getValue());
        }

    }

    /**
     * Gets the ordered timeseries, the numbers/dates are equals to the ones shown in the chart
     * 
     * @return installations per month
     */
    public SortedMap<Date, Integer> getSortedSeries() {
        return sortedSeries;
    }

    /**
     * Gets the google charts url displaying the data on a line chart
     * 
     * @return final url to render chart
     */
    public String renderChartUrl(boolean encode) {
        final List<Integer> numbers = new ArrayList<Integer>();
        final List<String> months = new ArrayList<String>();

        SimpleDateFormat df = new SimpleDateFormat("MM");// "yy.MM" - there is not a lot of space on the chart...

        for (Entry<Date, Integer> entry : sortedSeries.entrySet()) {
            months.add(df.format(entry.getKey()));
            final Integer value = entry.getValue();
            numbers.add(value);
            if (value > maxNumber) {
                maxNumber = value;
            }
        }

        final String numberStr = StringUtils.join(numbers, ',');
        final String monthStr = StringUtils.join(months, '|');

        Object[] args = { pluginName, monthStr, numberStr, maxNumber.toString() };
        String url = new MessageFormat(QUERY_FORMAT).format(args);
        if (true) {
            try {
                URI uri = new URI("https", "chart.googleapis.com", "/chart", url, null);
                url = uri.toASCIIString();
            } catch (URISyntaxException e) {
                url = null;
            }
        }
        return url;
    }
}
