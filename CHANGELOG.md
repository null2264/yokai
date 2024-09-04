<!-- Formatting
## Additions  ?? New features

## Changes  ?? Behaviour changes

## Fixes  ?? Bugfixes

## Translation  ?? translation changes/updates

## Other  ?? Technical stuff, what happened behind the scene
-->
## Additions
- Sync DoH provider list with upstream (added Mullvad, Control D, Njalla, and Shecan)
- Added option to enable verbose logging

## Changes
- Enable 'Split Tall Images' by default (@Smol-Ame)
- Minor visual adjustments

## Fixes
- Fixed only few DoH provider is actually being used (Cloudflare, Google, AdGuard, and Quad9)
- Fixed "Group by Ungrouped" showing duplicate entries
- Fixed reader sometimes won't load images
- Handle some uncaught crashes
- Fixed potential NPE crashes

## Other
- Simplify network helper code
- Even more SQLDelight migration effort
- Update dependency com.android.tools:desugar_jdk_libs to v2.1.2
- Update moko to v0.24.2
- Refactor trackers to use DTOs (@MajorTanya)
