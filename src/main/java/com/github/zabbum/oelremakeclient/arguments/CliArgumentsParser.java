package com.github.zabbum.oelremakeclient.arguments;

public class CliArgumentsParser {
    public static Arguments parseArguments(String[] args) {
        var argumentsBuilder = Arguments.builder();

        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=");
                String key = parts[0];
                String value;

                // If argument doesn't have value, use itself as value
                if (parts.length == 1) {
                    value = parts[0];
                }

                // If argument has value, use the value
                else {
                    value = parts[1];
                }

                switch (key) {
                    case "devmode" -> argumentsBuilder.devMode(true);
                    case "serverAddress" -> argumentsBuilder.serverAddress(value);
                    case "lang" -> argumentsBuilder.lang(value);
                }
            }
        }

        return argumentsBuilder.build();
    }
}
