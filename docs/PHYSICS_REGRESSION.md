# Physics regression matrix

This is the reference table for the modern-Java behaviour the backport targets. The automated suite is the source of
truth; this table records what each case means and where it is covered.

| Behaviour | Modern Java reference | Backport behaviour | Evidence |
|---|---|---|---|
| Item in still water | Rises slowly toward the surface | Rises with the configured lift, damping and surface offset | Game phase 0/7; `FloatingItemPolicyTest` |
| Item in air | Falls under gravity | Falls under vanilla gravity | Game phase 0 |
| Item in lava | Does not receive water buoyancy | Rejected by the liquid policy | Game phase 0/12 |
| Downward-flowing water | Current can carry the item down | Vanilla 1.7.10 current wins over the slow lift | Game phase 6 |
| Horizontal-flowing water | Current pushes the item horizontally | Vanilla 1.7.10 current moves the item; horizontal damping only reduces sliding | Game phase 15 |
| Source vs flowing water | The current, not the source flag, decides the result | Same; detection is liquid-based and current comes from vanilla | Game phases 6/15 |
| Partially submerged item | Any water overlap can float the item | Contracted bounding-box scan | Game phase 7 |
| Chunk boundary | Entity physics works across chunk borders | Same, with a Forge chunk ticket in the test server | Game phase 8 |
| Item NBT | Stack metadata survives entity save/load | Entity NBT round trip and live tick preservation | Game phase 9; `FloatingItemEventTest` |
| Item merge | Nearby matching stacks merge | Vanilla `EntityItem` merge path | Game phase 10 |
| Item despawn | Lifespan expiry removes the item | Vanilla lifespan path | Game phase 11 |
| Config reload | Config changes apply without restart | Config event and `/floatingitems reload` | Game phase 5; `FloatingItemsCommandTest` |
| Item filter | Only allowed items float | Blacklist/whitelist modes and scopes | Game phases 1-5/13/16; config tests |
| Debug counters | Diagnostics are opt-in | `Enable Debug Counters` | Game phase 17; `DebugCountersTest` |

## Compatibility

| Component | Supported |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| MCP mappings | `stable_12` |
| GTNH buildscript | 2.0.34 line |
| UniMixins | 0.3.x, required |
| Java | 8 bytecode and Java 8 test/toolchain |

The mod uses only 1.7.10 APIs in production code. The game and smoke test source sets are not shipped.
