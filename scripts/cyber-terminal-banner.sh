#!/usr/bin/env bash
# ==============================================================================
# Cyber-Terminal Aesthetics Engine
# Designed for Termux, Ghostty Android Terminal, & Haven
# Uses ANSI SGR, TrueColor (24-bit), Unicode Bento Grids & OKLCH Pastels
# ==============================================================================

set -euo pipefail

# --- Color Definitions (TrueColor 24-bit ESC[38;2;r;g;bm) ---
RESET="\033[0m"
BOLD="\033[1m"
DIM="\033[2m"
ITALIC="\033[3m"
UNDERLINE="\033[4m"

# App-System OKLCH Perceptual Developer Pastels
PASTEL_ROSE="\033[38;2;255;179;186m"     # #FFB3BA (Triggers/Warnings)
PASTEL_PEACH="\033[38;2;255;204;182m"    # #FFCCB6 (Warm Claude Accents)
PASTEL_AMBER="\033[38;2;255;229;153m"    # #FFE599 (Timers/Notice)
PASTEL_MINT="\033[38;2;168;230;207m"     # #A8E6CF (Success/Actions)
PASTEL_CYAN="\033[38;2;160;231;229m"     # #A0E7E5 (Telemetry/MQTT)
PASTEL_SKY="\033[38;2;180;197;228m"      # #B4C5E4 (Logic/Branching)
PASTEL_LAVENDER="\033[38;2;212;193;236m" # #D4C1EC (Variables/Scripts)

# Cyber Obsidian Accents
ELECTRIC_CYAN="\033[38;2;56;189;248m"   # #38BDF8
NEON_EMERALD="\033[38;2;16;185;129m"    # #10B981
WARM_AMBER="\033[38;2;255;184;77m"      # #FFB84D
SLATE_MUTED="\033[38;2;100;116;139m"    # #64748B
SLATE_BORDER="\033[38;2;51;65;85m"      # #334155
SLATE_FG="\033[38;2;203;213;225m"        # #CBD5E1

# Backgrounds
BG_CARD="\033[48;2;24;27;38m"           # Translucent Obsidian Glass
BG_BADGE="\033[48;2;30;41;59m"          # Dark Slate Pill
BG_CYAN="\033[48;2;14;116;144m"         # Cyan Pill
BG_MINT="\033[48;2;6;78;59m"            # Mint Pill
BG_AMBER="\033[48;2;120;53;15m"         # Amber Pill

BORDER_COLOR="\033[38;2;56;75;102m"

box_line() {
    local text="$1"
    # Print left border, text, and right border
    printf "%b│%b  %-62b%b│%b\n" "$BORDER_COLOR" "$RESET" "$text" "$BORDER_COLOR" "$RESET"
}

# --- 1. Header Banner ---
printf "%b╭────────────────────────────────────────────────────────────────╮%b\n" "$BORDER_COLOR" "$RESET"
box_line "${ELECTRIC_CYAN}${BOLD}⚡ GHOSTTY × HAVEN CORE${RESET} ${SLATE_MUTED}•${RESET} ${PASTEL_MINT}● ONLINE${RESET} ${SLATE_MUTED}[120 FPS JIT]${RESET}"
box_line "${SLATE_MUTED}${ITALIC}Sub-pixel smooth rendering, Kitty graphics & OKLCH pastels${RESET}"
printf "%b├────────────────────────────────────────────────────────────────┤%b\n" "$BORDER_COLOR" "$RESET"

# --- 2. System Telemetry Grid ---
UP_INFO=$(uptime -p 2>/dev/null | sed 's/up //' || echo "online")
ARCH_INFO=$(uname -m)
KERNEL_INFO=$(uname -r | cut -d'-' -f1)

box_line "${PASTEL_SKY}${BOLD}TELEMETRY${RESET}   ${BG_BADGE}${PASTEL_CYAN} ARCH: ${ARCH_INFO} ${RESET}  ${BG_BADGE}${PASTEL_MINT} KERNEL: ${KERNEL_INFO} ${RESET}  ${BG_BADGE}${PASTEL_AMBER} ${UP_INFO} ${RESET}"
box_line "${SLATE_MUTED}Rendering :${RESET} ${NEON_EMERALD}████████████████████████░░░░░░${RESET} ${PASTEL_MINT}80%${RESET} ${SLATE_MUTED}[GPU VSYNC]${RESET}"
printf "%b├────────────────────────────────────────────────────────────────┤%b\n" "$BORDER_COLOR" "$RESET"

# --- 3. Markdown Formatting Hierarchy Showcase ---
box_line "${PASTEL_PEACH}${BOLD}HIERARCHICAL TEXT RENDERING AUDIT${RESET}"
box_line "${ELECTRIC_CYAN}${BOLD}# Heading 1 (Electric Cyan 24-bit TrueColor)${RESET}"
box_line "${PASTEL_LAVENDER}${BOLD}## Heading 2 (Lavender Mauve Sub-Header)${RESET}"
box_line "${PASTEL_AMBER}### Heading 3 (Butter Amber Section Notice)${RESET}"
box_line "${SLATE_MUTED}│${RESET} ${SLATE_FG}${ITALIC}> Tactile blockquote with specular border highlight${RESET}"
box_line "${SLATE_FG}Plain text with ${BOLD}bold tokens${RESET}${SLATE_FG}, ${UNDERLINE}underlines${RESET}${SLATE_FG}, & ${BG_BADGE}${PASTEL_CYAN} cell_run() ${RESET}${SLATE_FG} pills.${RESET}"
printf "%b├────────────────────────────────────────────────────────────────┤%b\n" "$BORDER_COLOR" "$RESET"

# --- 4. Perceptual Developer Pastels Palette Bar ---
box_line "${SLATE_FG}OKLCH Pastels: ${PASTEL_ROSE}■ Rose ${PASTEL_PEACH}■ Peach ${PASTEL_AMBER}■ Amber ${PASTEL_MINT}■ Mint ${PASTEL_CYAN}■ Cyan ${PASTEL_SKY}■ Sky ${PASTEL_LAVENDER}■ Lavender${RESET}"
printf "%b╰────────────────────────────────────────────────────────────────╯%b\n" "$BORDER_COLOR" "$RESET"

# --- 5. Tactile Cybernetic Shell Prompt ---
echo ""
printf "  %b╭─%b %b%b 󰀄 %s@ghostty %b%b─%b%b%b  %s %b%b─%b%b%b  main* %b\n" \
    "$BORDER_COLOR" "$RESET" "$BG_BADGE" "$ELECTRIC_CYAN" "${USER:-root}" "$RESET" \
    "$BORDER_COLOR" "$RESET" "$BG_BADGE" "$PASTEL_SKY" "$(basename "$PWD")" "$RESET" \
    "$BORDER_COLOR" "$RESET" "$BG_BADGE" "$PASTEL_MINT" "$RESET"
printf "  %b╰─❯%b " "$BORDER_COLOR" "$RESET"
echo ""
