package com.lavochk.listTelegram;

import org.bukkit.configuration.ConfigurationSection;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ChartGenerator {

    private static final String API_URL = "https://quickchart.io/chart?c=";

    public static String generateChartUrl(ConfigurationSection dailyPlaytime) {
        if (dailyPlaytime == null) {
            return null;
        }

        List<String> labels = new ArrayList<>(dailyPlaytime.getKeys(false));
        Collections.sort(labels); // Sort dates chronologically

        // Limit to last 15 days for readability
        if (labels.size() > 15) {
            labels = labels.subList(labels.size() - 15, labels.size());
        }

        List<Double> data = labels.stream()
                .map(day -> dailyPlaytime.getLong(day) / 3600.0) // Convert seconds to hours
                .collect(Collectors.toList());

        String labelsString = labels.stream().map(l -> "'" + l.substring(5) + "'").collect(Collectors.joining(","));
        String dataString = data.stream().map(d -> String.format("%.2f", d)).collect(Collectors.joining(","));

        String chartConfig = "{"
                + "'type':'bar',"
                + "'data':{"
                + "'labels':[" + labelsString + "],"
                + "'datasets':["
                + "{'label':'Часы в игре','data':[" + dataString + "],'backgroundColor':'rgba(54, 162, 235, 0.5)','borderColor':'rgb(54, 162, 235)','borderWidth':1}"
                + "]"
                + "},"
                + "'options':{'title':{'display':true,'text':'Активность за последние дни'}}"
                + "}";

        try {
            return API_URL + URLEncoder.encode(chartConfig, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
