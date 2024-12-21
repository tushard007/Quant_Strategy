package org.factor_investing.quant_strategy.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 findMatchesAndNonMatches(List<T> list1, List<T> list2, Function<T, K> keyExtractor):
 T: The type of the objects in the lists.
 K: The type of the property used for comparison (e.g., Integer for id).
 keyExtractor: A function to extract the property for comparison.
 */
public class GenericMatchingUtility {
/*
    The method takes two lists of objects of type T and a function keyExtractor that extracts a property of type K from the objects.
    It returns a map with three lists:
    - Matching: The elements that are present in both lists.
    - NotMatchingInList1: The elements that are present in list1 but not in list2.
    - NotMatchingInList2: The elements that are present in list2 but not in list1.
      // Example
        Map<String, List<Product>> result = findMatchesAndNonMatches(
                list1, list2, product -> product.id);
 */
    public static <T, K> Map<String, List<T>> findMatchesAndNonMatches(
            List<T> list1,
            List<T> list2,
            Function<T, K> keyExtractor) {

        // Find matching elements
        List<T> matching = list1.stream()
                .filter(item1 -> list2.stream()
                        .anyMatch(item2 -> keyExtractor.apply(item1).equals(keyExtractor.apply(item2))))
                .collect(Collectors.toList());

        // Find non-matching elements in list1
        List<T> notMatchingInList1 = list1.stream()
                .filter(item1 -> list2.stream()
                        .noneMatch(item2 -> keyExtractor.apply(item1).equals(keyExtractor.apply(item2))))
                .collect(Collectors.toList());

        // Find non-matching elements in list2
        List<T> notMatchingInList2 = list2.stream()
                .filter(item2 -> list1.stream()
                        .noneMatch(item1 -> keyExtractor.apply(item2).equals(keyExtractor.apply(item1))))
                .collect(Collectors.toList());

        // Store results in a map
        Map<String, List<T>> result = new HashMap<>();
        result.put("Matching", matching);
        result.put("NotMatchingInList1", notMatchingInList1);
        result.put("NotMatchingInList2", notMatchingInList2);

        return result;
    }
}
