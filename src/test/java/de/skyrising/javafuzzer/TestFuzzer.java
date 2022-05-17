package de.skyrising.javafuzzer;

import java.nio.file.Path;

public class TestFuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -javaagent:coverage.jar -jar javafuzzer.jar <target>");
            System.exit(1);
        }
        String targetClass = args[0];
        try {
            Class<?> clazz = Class.forName(targetClass);
            FuzzTarget target = (FuzzTarget) clazz.getConstructor().newInstance();
            Corpus corpus = new Corpus(Path.of("corpus"), Path.of("seed"), "class");
            Fuzzer fuzzer = new Fuzzer(corpus, target, Path.of("crash"));
            fuzzer.start(28);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
