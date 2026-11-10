/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2026 Sylirre */

package io.github.sylirre.terminal.term;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * What a {@link TerminalSession} spawns: either a command to execve() or an
 * arm64chroot argv to run in-process ({@link TerminalNative#ptyCreateEmulator}).
 * Built by {@link #androidShell} or {@link UserlandRootfs#command}.
 */
public final class SessionCommand {

    /** execve() path; null means "enter arm64chroot_main() with argv". */
    public final String cmd;
    public final String[] argv;
    public final String[] env;
    public final String cwd;
    /** Default tab-title prefix while the shell hasn't set one (OSC 0/2). */
    public final String label;
    /** True for a arm64chroot session (vs. the plain Android shell). */
    public final boolean userland;

    SessionCommand(String cmd, String[] argv, String[] env, String cwd,
            String label, boolean userland) {
        this.cmd = cmd;
        this.argv = argv;
        this.env = env;
        this.cwd = cwd;
        this.label = label;
        this.userland = userland;
    }

    /**
     * /system/bin/sh with PATH=/system/bin.
     *
     * @param homeDir HOME and initial working directory (app files dir —
     *                the only generally writable place).
     * @param tmpDir  TMPDIR (the app's {@code files/tmp}, see {@link TmpDir}).
     */
    private static void ensureShellRc(String homeDir) {
        try {
            File rc = new File(homeDir, ".shrc");
            String content = "# A-SH Shell Config (Termux-style command header)\n" +
                    "export TERM=xterm-256color\n" +
                    "export COLORTERM=truecolor\n" +
                    "export HISTFILE=\"$HOME/.sh_history\"\n" +
                    "export HISTSIZE=10000\n" +
                    "\n" +
                    "if [ -n \"$KSH_VERSION\" ]; then\n" +
                    "  _ash_prompt() {\n" +
                    "    local p=\"$PWD\"\n" +
                    "    case \"$p\" in\n" +
                    "      *io.github.sylirre.terminal/files*)\n" +
                    "        p=\"~${p#*io.github.sylirre.terminal/files}\"\n" +
                    "        ;;\n" +
                    "      *)\n" +
                    "        if [ -n \"$HOME\" ] && [ \"$HOME\" != \"/\" ]; then\n" +
                    "          case \"$p\" in\n" +
                    "            \"$HOME\"*) p=\"~${p#$HOME}\" ;;\n" +
                    "          esac\n" +
                    "        fi\n" +
                    "        ;;\n" +
                    "    esac\n" +
                    "    [ -z \"$p\" ] && p=\"~\"\n" +
                    "    local s=\"$\"\n" +
                    "    [ \"$(id -u 2>/dev/null)\" = \"0\" ] && s=\"#\"\n" +
                    "    REPLY=\"$p\n$s \"\n" +
                    "  }\n" +
                    "  PS1='${| _ash_prompt; }'\n" +
                    "elif [ -n \"$BASH_VERSION\" ]; then\n" +
                    "  PS1='\\w\\n\\$ '\n" +
                    "else\n" +
                    "  PS1='$ '\n" +
                    "fi\n" +
                    "\n" +
                    "alias ll='ls -l'\n" +
                    "alias la='ls -la'\n" +
                    "alias ..='cd ..'\n" +
                    "alias ...='cd ../..'\n";
            Files.write(rc.toPath(), content.getBytes(StandardCharsets.UTF_8));
            File profile = new File(homeDir, ".profile");
            Files.write(profile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            File mkshrc = new File(homeDir, ".mkshrc");
            Files.write(mkshrc.toPath(), content.getBytes(StandardCharsets.UTF_8));
            File bashrc = new File(homeDir, ".bashrc");
            Files.write(bashrc.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static SessionCommand androidShell(String homeDir, String tmpDir) {
        ensureShellRc(homeDir);
        String[] env = {
                "PATH=/system/bin",
                "HOME=" + homeDir,
                "TMPDIR=" + tmpDir,
                "ENV=" + homeDir + "/.shrc",
                "PS1=$ ",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=en_US.UTF-8",
                "ANDROID_ROOT=/system",
                "ANDROID_DATA=/data",
        };
        return new SessionCommand("/system/bin/sh", new String[] {"sh"}, env,
                homeDir, "sh", false);
    }

    /**
     * Spawns a privileged Root shell via su (/system/bin/su, /system/xbin/su, etc.).
     */
    public static SessionCommand rootShell(String homeDir, String tmpDir) {
        ensureShellRc(homeDir);
        String suPath = "/system/bin/su";
        String[] candidates = {
                "/system/bin/su",
                "/system/xbin/su",
                "/data/adb/ksu/bin/su",
                "/data/adb/ap/bin/su",
                "/data/adb/magisk/su"
        };
        for (String c : candidates) {
            if (new File(c).canExecute()) {
                suPath = c;
                break;
            }
        }
        String[] env = {
                "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/data/adb/ksu/bin:/data/adb/magisk",
                "HOME=/data/local/tmp",
                "TMPDIR=" + tmpDir,
                "ENV=" + homeDir + "/.shrc",
                "PS1=# ",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=en_US.UTF-8"
        };
        return new SessionCommand(suPath, new String[] {suPath, "-"}, env,
                homeDir, "root", false);
    }

    /**
     * Spawns a Shizuku / rish ADB-privileged shell.
     */
    public static SessionCommand shizukuShell(Context context, String homeDir, String tmpDir) {
        ensureShellRc(homeDir);
        File binDir = new File(homeDir, "bin");
        if (!binDir.exists()) binDir.mkdirs();
        File rishFile = new File(binDir, "rish");
        File dexFile = new File(binDir, "rish_shizuku.dex");

        if (context != null) {
            try {
                if (!rishFile.exists() || !dexFile.exists()) {
                    AssetManager am = context.getAssets();
                    try (InputStream in = am.open("rish/rish");
                         FileOutputStream out = new FileOutputStream(rishFile)) {
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                    }
                    rishFile.setExecutable(true, false);

                    try (InputStream in = am.open("rish/rish_shizuku.dex");
                         FileOutputStream out = new FileOutputStream(dexFile)) {
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                    }
                    dexFile.setReadOnly();
                }
            } catch (Exception ignored) {}
        }

        String pkgId = context != null ? context.getPackageName() : "io.github.sylirre.terminal";
        String[] env = {
                "PATH=" + binDir.getAbsolutePath() + ":/system/bin",
                "HOME=" + homeDir,
                "TMPDIR=" + tmpDir,
                "ENV=" + homeDir + "/.shrc",
                "PS1=$ ",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=en_US.UTF-8",
                "RISH_APPLICATION_ID=" + pkgId
        };

        if (rishFile.canExecute() && dexFile.exists()) {
            return new SessionCommand("/system/bin/sh",
                    new String[] {"/system/bin/sh", rishFile.getAbsolutePath()},
                    env, homeDir, "rish", false);
        }

        // Fallback: check Termux path
        String fallbackRish = "/data/data/com.termux/files/home/bin/rish";
        if (new File(fallbackRish).canExecute()) {
            return new SessionCommand("/system/bin/sh",
                    new String[] {"/system/bin/sh", fallbackRish},
                    env, homeDir, "rish", false);
        }

        return new SessionCommand("/system/bin/sh", new String[] {"sh"}, env,
                homeDir, "shizuku", false);
    }

    public static SessionCommand shizukuShell(String homeDir, String tmpDir) {
        return shizukuShell(null, homeDir, tmpDir);
    }

    /**
     * Bridge into Termux native environment.
     * Attempts to launch Termux's bash environment directly via su, rish, or SSH.
     */
    public static SessionCommand termuxBridge(String homeDir, String tmpDir) {
        String termuxBash = "/data/data/com.termux/files/usr/bin/bash";
        String rishPath = "/data/data/com.termux/files/home/bin/rish";
        String suPath = "/system/bin/su";

        if (new java.io.File(suPath).canExecute()) {
            String[] env = {
                    "PREFIX=/data/data/com.termux/files/usr",
                    "HOME=/data/data/com.termux/files/home",
                    "PATH=/data/data/com.termux/files/usr/bin:/system/bin",
                    "TMPDIR=/data/data/com.termux/files/usr/tmp",
                    "TERM=xterm-256color",
                    "COLORTERM=truecolor",
                    "LANG=en_US.UTF-8"
            };
            return new SessionCommand(suPath, new String[] {suPath, "-c",
                    "export PREFIX=/data/data/com.termux/files/usr; " +
                    "export HOME=/data/data/com.termux/files/home; " +
                    "export PATH=$PREFIX/bin:$PATH; " +
                    "exec /data/data/com.termux/files/usr/bin/bash -l"},
                    env, homeDir, "termux", false);
        }

        if (new java.io.File(rishPath).canExecute()) {
            String[] env = {
                    "PREFIX=/data/data/com.termux/files/usr",
                    "HOME=/data/data/com.termux/files/home",
                    "PATH=/data/data/com.termux/files/usr/bin:/system/bin",
                    "TMPDIR=" + tmpDir,
                    "TERM=xterm-256color",
                    "COLORTERM=truecolor",
                    "LANG=en_US.UTF-8",
                    "RISH_APPLICATION_ID=io.github.sylirre.terminal"
            };
            return new SessionCommand("/system/bin/sh", new String[] {"/system/bin/sh", rishPath},
                    env, homeDir, "termux", false);
        }

        // Fallback to Android shell with Termux env configured
        String[] env = {
                "PREFIX=/data/data/com.termux/files/usr",
                "HOME=/data/data/com.termux/files/home",
                "PATH=/data/data/com.termux/files/usr/bin:/system/bin",
                "TMPDIR=" + tmpDir,
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8"
        };
        return new SessionCommand("/system/bin/sh", new String[] {"sh"}, env,
                homeDir, "termux", false);
    }

    /**
     * SSH connection session.
     */
    public static SessionCommand sshCommand(String host, int port, String user, String homeDir, String tmpDir) {
        String target = (user != null && !user.isEmpty()) ? (user + "@" + host) : host;
        String[] argv = new String[] {"ssh", "-p", String.valueOf(port), target};
        String[] env = {
                "PATH=/system/bin:/usr/bin:/bin",
                "HOME=" + homeDir,
                "TMPDIR=" + tmpDir,
                "TERM=xterm-256color"
        };
        return new SessionCommand("/system/bin/sh", argv, env, homeDir, "ssh:" + host, false);
    }

    /**
     * Default tab label for a login shell: the basename of its executable path,
     * e.g. {@code /system/bin/sh -> "sh"}, {@code /bin/bash -> "bash"}. Any
     * arguments must already be stripped by the caller. Falls back to
     * {@code "sh"} for a null/empty or slash-only path.
     */
    static String labelForShell(String shellPath) {
        if (shellPath == null) return "sh";
        String p = shellPath.trim();
        int end = p.length();
        while (end > 0 && p.charAt(end - 1) == '/') end--; // drop trailing slashes
        String base = p.substring(p.lastIndexOf('/', end - 1) + 1, end);
        return base.isEmpty() ? "sh" : base;
    }
}
