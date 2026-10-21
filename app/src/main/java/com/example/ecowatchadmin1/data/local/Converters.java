package com.example.ecowatchadmin1.data.local;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    private static final Type LIST_OF_STRING = new TypeToken<List<String>>() {}.getType();

    /**
     * Reads a stored list column. A NULL, blank or malformed value yields an
     * empty list so a single bad column cannot break every read of the table, and
     * callers never have to null-check the result.
     */
    @TypeConverter
    public static List<String> fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = new Gson().fromJson(value, LIST_OF_STRING);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Stores a list as JSON. An absent list is written as SQL NULL rather than
     * the literal text "null", so "no photos" stays distinguishable from a row
     * that was never populated.
     */
    @TypeConverter
    public static String fromList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return new Gson().toJson(list, LIST_OF_STRING);
    }
}
