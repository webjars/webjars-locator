package org.webjars;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import org.apache.commons.text.similarity.LevenshteinDistance;

class LevenshteinDistanceComparator implements Comparator<String>, Serializable {

    private static final long serialVersionUID = -2751197414972242535L
        ;
    private final HashMap<String, Integer> distanceMap = new HashMap<>();
    private final String nameForComparisons;

    LevenshteinDistanceComparator(String nameForComparisons) {
        this.nameForComparisons = nameForComparisons;
    }

    @Override
    public int compare(String o1, String o2) {
        return Integer.compare(getDistance(o1), getDistance(o2));
    }

    private int getDistance(String value) {
        String lowerCaseValue = value.toLowerCase(Locale.ENGLISH);
        if (distanceMap.containsKey(lowerCaseValue)) {
            return distanceMap.get(lowerCaseValue);
        }
        int distance = LevenshteinDistance.getDefaultInstance().apply(nameForComparisons, lowerCaseValue);
        distanceMap.put(lowerCaseValue, distance);
        return distance;
    }

}
