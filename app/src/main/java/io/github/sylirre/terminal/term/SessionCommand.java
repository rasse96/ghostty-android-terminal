/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2026 Sylirre */

package io.github.sylirre.terminal.term;

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
            java.io.File rc = new java.io.File(homeDir, ".shrc");
            if (!rc.exists()) {
                String content = "# A-SH Shell Config\n" +
                        "export TERM=xterm-256color\n" +
                        "export COLORTERM=truecolor\n" +
                        "export HISTFILE=\"$HOME/.sh_history\"\n" +
                        "export HISTSIZE=10000\n" +
                        "export PS1='\\[\\033[1;36m\\]λ a-sh\\[\\033[0m\\]:\\[\\033[1;34m\\]\\w\\[\\033[0m\\] \\$ '\n" +
                        "alias ll='ls -l'\n" +
                        "alias la='ls -la'\n" +
                        "alias ..='cd ..'\n" +
                        "alias ...='cd ../..'\n" +
                        "alias grep='grep --color=auto'\n" +
                        "bind 'set enable-bracketed-paste on' 2>/dev/null || true\n";
                java.nio.file.Files.write(rc.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    public static SessionCommand androidShell(String homeDir, String tmpDir) {
        ensureShellRc(homeDir);
        String[] env = {
                "PATH=/system/bin",
                "HOME=" + homeDir,
                "TMPDIR=" + tmpDir,
                "ENV=" + homeDir + "/.shrc",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=en_US.UTF-8",
                "ANDROID_ROOT=/system",
                "ANDROID_DATA=/data",
        };
        return new SessionCommand("/system/bin/sh", new String[] {"sh"}, env,
                homeDir, labelForShell("/system/bin/sh"), false);
    }

    /**
     * Spawns a privileged Root shell via su (/system/bin/su, /system/xbin/su, etc.).
     */
    public static SessionCommand rootShell(String homeDir, String tmpDir) {
        String suPath = "/system/bin/su";
        String[] candidates = {
                "/system/bin/su",
                "/system/xbin/su",
                "/data/adb/ksu/bin/su",
                "/data/adb/ap/bin/su",
                "/data/adb/magisk/su"
        };
        for (String c : candidates) {
            if (new java.io.File(c).canExecute()) {
                suPath = c;
                break;
            }
        }
        String[] env = {
                "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/data/adb/ksu/bin:/data/adb/magisk",
                "HOME=/data/local/tmp",
                "TMPDIR=" + tmpDir,
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8"
        };
        return new SessionCommand(suPath, new String[] {suPath, "-"}, env,
                homeDir, "root", false);
    }

    /**
     * Spawns a Shizuku / rish ADB-privileged shell.
     */
    public static SessionCommand shizukuShell(String homeDir, String tmpDir) {
        String rishPath = "/data/data/com.termux/files/home/bin/rish";
        String[] env = {
                "PATH=/system/bin:/data/data/com.termux/files/home/bin",
                "HOME=" + homeDir,
                "TMPDIR=" + tmpDir,
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8",
                "RISH_APPLICATION_ID=io.github.sylirre.terminal"
        };
        if (new java.io.File(rishPath).canExecute()) {
            return new SessionCommand("/system/bin/sh", new String[] {"/system/bin/sh", rishPath},
                    env, homeDir, "rish", false);
        }
        return new SessionCommand("/system/bin/sh", new String[] {"sh"}, env,
                homeDir, "shizuku", false);
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
