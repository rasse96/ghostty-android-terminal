/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2026 Sylirre */

package io.github.sylirre.terminal.term;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.sylirre.terminal.R;

/**
 * Declares an execution environment profile (Alpine/Debian userland, Termux bridge,
 * Root su, Shizuku rish, Android shell, or Guest VM) and handles command resolution.
 */
public final class SessionProfile {

    public enum Type {
        USERLAND_ALPINE("Alpine Linux", "Lightweight userland with apk", "λ"),
        USERLAND_DEBIAN("Debian Linux", "glibc userland with apt, python & gcc", "δ"),
        TERMUX_BRIDGE("Termux Native", "Termux environment & packages", "tx"),
        ROOT_SU("Root Shell (su)", "Magisk / KernelSU / APatch root access", "#"),
        SHIZUKU_RISH("Shizuku (rish)", "ADB-level privileged shell", "⚡"),
        ANDROID_SHELL("Android Shell", "Standard /system/bin/sh", "$"),
        GUEST_VM("Guest Linux VM", "arm64emu virtual machine console", "vm"),
        SSH("SSH Quick Connect", "Connect to Termux or remote host", "ssh");

        public final String defaultName;
        public final String defaultDesc;
        public final String glyph;

        Type(String defaultName, String defaultDesc, String glyph) {
            this.defaultName = defaultName;
            this.defaultDesc = defaultDesc;
            this.glyph = glyph;
        }
    }

    public final Type type;
    public final String name;
    public final String description;
    public final String glyph;

    public SessionProfile(Type type, String name, String description, String glyph) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.glyph = glyph;
    }

    public static SessionProfile of(Type type) {
        return new SessionProfile(type, type.defaultName, type.defaultDesc, type.glyph);
    }

    /**
     * Resolves the profile into a SessionCommand to launch.
     */
    public SessionCommand buildCommand(Context context, UserlandOptions userlandOpts) throws IOException {
        String homeDir = context.getFilesDir().getAbsolutePath();
        String tmpDir = TmpDir.ensure(context);

        switch (type) {
            case USERLAND_ALPINE:
            case USERLAND_DEBIAN:
                if (UserlandRootfs.isInstalled(context)) {
                    return UserlandRootfs.command(context, userlandOpts != null ? userlandOpts : UserlandOptions.defaults(null));
                }
                return SessionCommand.androidShell(homeDir, tmpDir);

            case TERMUX_BRIDGE:
                return SessionCommand.termuxBridge(homeDir, tmpDir);

            case ROOT_SU:
                return SessionCommand.rootShell(homeDir, tmpDir);

            case SHIZUKU_RISH:
                return SessionCommand.shizukuShell(context, homeDir, tmpDir);

            case SSH:
                return SessionCommand.sshCommand("127.0.0.1", 8022, null, homeDir, tmpDir);

            case ANDROID_SHELL:
            default:
                return SessionCommand.androidShell(homeDir, tmpDir);
        }
    }

    /**
     * Returns the list of standard available profiles.
     */
    public static List<SessionProfile> availableProfiles(Context context) {
        List<SessionProfile> list = new ArrayList<>();
        list.add(of(Type.USERLAND_ALPINE));
        list.add(of(Type.TERMUX_BRIDGE));
        list.add(of(Type.SHIZUKU_RISH));
        list.add(of(Type.ROOT_SU));
        list.add(of(Type.ANDROID_SHELL));
        list.add(of(Type.GUEST_VM));
        list.add(of(Type.SSH));
        return list;
    }
}
