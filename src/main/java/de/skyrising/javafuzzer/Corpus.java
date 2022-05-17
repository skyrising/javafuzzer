package de.skyrising.javafuzzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static de.skyrising.javafuzzer.RandomUtils.*;

public class Corpus {
    private static final Mutation[] AVAILABLE_MUTATIONS = Mutation.values();
    private final Random random = new Random();
    private final List<Input> inputs = new ArrayList<>();
    private final List<Input> crashInputs = new ArrayList<>();
    private final ThreadLocal<Queue<Input>> queue = ThreadLocal.withInitial(ArrayDeque::new);
    private final Path corpusPath;
    private final String fileExtension;

    public Corpus(Path corpusPath, Path seedPath, String fileExtension) {
        this.corpusPath = corpusPath;
        this.fileExtension = fileExtension;
        try {
            if (seedPath != null) {
                Files.walkFileTree(seedPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        inputs.add(new Input(Files.readAllBytes(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.createDirectories(corpusPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized void add(byte[] data) {
        Input input = new Input(data);
        if (inputs.contains(input)) return;
        inputs.add(input);
        writeFile(input.bytes());
    }

    public synchronized void addCrash(byte[] data) {
        Input input = new Input(data);
        if (crashInputs.contains(input)) return;
        crashInputs.add(input);
    }

    private byte[] pickBase() {
        Queue<Input> queue = this.queue.get();
        Input base = queue.poll();
        if (base == null) {
            if (inputs.isEmpty() && crashInputs.isEmpty()) return new byte[0];
            synchronized (this) {
                queue.addAll(crashInputs);
                queue.addAll(inputs);
            }
            base = queue.poll();
        }
        return Objects.requireNonNull(base).bytes();
        /*boolean pickCrash = this.inputs.isEmpty() || random.nextInt(10) == 0;
        List<Input> inputs = pickCrash ? this.crashInputs : this.inputs;
        if (inputs.isEmpty()) return new byte[0];
        int offset = inputs.size() - 1 - Math.abs(triangular(random, inputs.size()));
        return inputs.get(offset).bytes();*/
    }

    public byte[] generate() {
        byte[] buf = pickBase().clone();
        Random random = ThreadLocalRandom.current();
        int mutationCount = 1 + bitsLogarithmic(random);
        for (int i = 0; i < mutationCount; i++) {
            buf = mutate(random, buf);
        }
        return buf;
    }

    private byte[] mutate(Random random, byte[] data) {
        if (data.length == 0) return Mutation.EXTEND.mutate(random, data, 4096);
        return AVAILABLE_MUTATIONS[random.nextInt(AVAILABLE_MUTATIONS.length)].mutate(random, data, 4096);
    }

    private void writeFile(byte[] data) {
        try {
            Files.write(corpusPath.resolve(generateFileName("input", data) + "." + fileExtension), data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String generateFileName(String prefix, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            String name = Base64.getUrlEncoder().encodeToString(hash);
            return prefix + "-" + name.substring(0, name.length() - 1);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private record Input(byte[] bytes) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Input && Arrays.equals(((Input) obj).bytes, bytes);
        }
    }

    private enum Mutation {
        EXTEND {
            @Override
            byte[] mutate(Random rand, byte[] data, int maxLength) {
                if (data.length >= maxLength) return data;
                return Arrays.copyOf(data, data.length + pickLength(rand, maxLength - data.length));
            }
        }, SET_RANDOM_BYTE {
            @Override
            byte[] mutate(Random rand, byte[] data, int maxLength) {
                int index = rand.nextInt(data.length);
                data[index] = (byte) rand.nextInt(256);
                return data;
            }
        }, COPY_RANGE {
            @Override
            byte[] mutate(Random rand, byte[] data, int maxLength) {
                int srcStart = rand.nextInt(data.length);
                int srcEnd = srcStart + pickLength(rand, data.length - srcStart);
                int dstStart = rand.nextInt(data.length);
                int length = Math.min(srcEnd - srcStart, data.length - dstStart);
                System.arraycopy(data, srcStart, data, dstStart, length);
                return data;
            }
        }, ADD_SUBTRACT_BYTE {
            @Override
            byte[] mutate(Random rand, byte[] data, int maxLength) {
                int index = rand.nextInt(data.length);
                data[index] = (byte) (data[index] + rand.nextInt(32) - 16);
                return data;
            }
        }, TRUNCATE {
            @Override
            byte[] mutate(Random rand, byte[] data, int maxLength) {
                return Arrays.copyOf(data, data.length - pickLength(rand, data.length));
            }
        };

        abstract byte[] mutate(Random rand, byte[] data, int maxLength);
    }
}
