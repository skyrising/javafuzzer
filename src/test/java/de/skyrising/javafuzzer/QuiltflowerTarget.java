package de.skyrising.javafuzzer;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.jar.Manifest;

public class QuiltflowerTarget implements FuzzTarget {
    @Override
    public void fuzz(byte[] data) {
        var decompiler = new BaseDecompiler((s, s1) -> data, new IResultSaver() {
            @Override
            public void saveFolder(String s) {
            }

            @Override
            public void copyFile(String s, String s1, String s2) {
            }

            @Override
            public void saveClassFile(String s, String s1, String s2, String s3, int[] ints) {
            }

            @Override
            public void createArchive(String s, String s1, Manifest manifest) {
            }

            @Override
            public void saveDirEntry(String s, String s1, String s2) {
            }

            @Override
            public void copyEntry(String s, String s1, String s2, String s3) {
            }

            @Override
            public void saveClassEntry(String s, String s1, String s2, String s3, String s4) {
            }

            @Override
            public void closeArchive(String s, String s1) {
            }
        }, IFernflowerPreferences.getDefaults(), new IFernflowerLogger() {
            @Override public void writeMessage(String s, Severity severity) {}
            @Override public void writeMessage(String s, Severity severity, Throwable throwable) {}
        });
        decompiler.addSource(new File("Input.class"));
        decompiler.decompileContext();
    }

    static {
        new QuiltflowerTarget().fuzz(new byte[0]);
    }
}
