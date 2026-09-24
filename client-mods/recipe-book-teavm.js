/* EasierCrafting for JasperCraft -- a port of Giselbaer's EasierCrafting (1.12-forge, 1.2).
 *
 * The original hooks GuiOpenEvent, swaps GuiCrafting/GuiInventory for subclasses, and draws
 * a panel of everything you can currently craft beside the window: grouped by creative tab,
 * scrollable, searchable, with the ingredients shown under the cursor and a click that loads
 * the grid for you. All of that is reproduced here.
 *
 * Two things had to change, and both are forced by the platform rather than chosen:
 *
 * 1. There are no subclasses to swap in. The browser client is a whole-program compile, so
 *    instead of replacing the screen this patches the two screens' own foreground-draw,
 *    mouse and key methods. Same result, no class hierarchy needed.
 *
 * 2. Minecraft 1.12 never sends recipes to the client -- the vanilla recipe book works off
 *    the client's own copy and the server only syncs which ones are unlocked. So a purely
 *    client-side browser cannot see the 89 JasperCraft recipes at all. They are compiled in
 *    below instead, generated from the same table the server registers from, so the two
 *    cannot drift. Vanilla recipes are deliberately left to Minecraft's own recipe book,
 *    which already lists them and which this leaves switched on; between the two you can see
 *    everything. Crafting stays server-authoritative either way -- this only moves items
 *    into the grid, exactly like the original, and the server decides what comes out.
 *
 * Everything the game itself has to do -- drawing an item, reading a slot, clicking one --
 * is a TeaVM suspending call, so those live in small state machines at the bottom of this
 * file that do nothing but walk a list. All of the actual logic is plain JavaScript above
 * them, which is what keeps the state machines simple enough to be obviously correct.
 *
 * Every entry point is wrapped. Any error disables the book for the rest of the session and
 * the screen falls back to stock behaviour, because a crafting table that will not open is
 * far worse than one without a recipe list.
 */
var JasprBlueprintTable = [{"id":"sepulcher","shape":["iII","SRU",".I."],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"vesper","shape":["iII","SRT",".Ui"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"ossuary","shape":["iiI","SRU",".GI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"}}},{"id":"turnstile","shape":["iIF","SRU",".Ii"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"cinder","shape":["iII","SRU","Iri"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"}}},{"id":"whisper","shape":["iiI","SRT","IUi"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"tunnelrat","shape":["iII","SRU","iFI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"}}},{"id":"tempest","shape":["iiI","StU","iRI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"t":{"tag":"repeater:0","snbt":"{id:\"minecraft:repeater\",Count:1b,Damage:0s}","name":"Redstone Repeater"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"}}},{"id":"blackbox","shape":["iiI","StU","iQI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"t":{"tag":"repeater:0","snbt":"{id:\"minecraft:repeater\",Count:1b,Damage:0s}","name":"Redstone Repeater"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"}}},{"id":"quarantine","shape":["iiI","SRU","iOI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"}}},{"id":"adjudicator","shape":["iiI","SRU","iGI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"}}},{"id":"bastion","shape":["iiI","OrU","iOI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"deadfrequency","shape":["iiI","GtU","irQ"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"t":{"tag":"repeater:0","snbt":"{id:\"minecraft:repeater\",Count:1b,Damage:0s}","name":"Redstone Repeater"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"}}},{"id":"whiteout","shape":["iiV","SRT","iUi"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"V":{"tag":"glass:0","snbt":"{id:\"minecraft:glass\",Count:1b,Damage:0s}","name":"Glass"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"frostbite","shape":["iiD","GrQ","iYI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"Y":{"tag":"prismarine_crystals:0","snbt":"{id:\"minecraft:prismarine_crystals\",Count:1b,Damage:0s}","name":"Prismarine Crystals"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"rifle","shape":["iiI","SRX","UGI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"}}},{"id":"longwatch","shape":["ViI","SRU","iQi"],"keys":{"V":{"tag":"glass:0","snbt":"{id:\"minecraft:glass\",Count:1b,Damage:0s}","name":"Glass"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"}}},{"id":"signal","shape":["ViI","StU","iri"],"keys":{"V":{"tag":"glass:0","snbt":"{id:\"minecraft:glass\",Count:1b,Damage:0s}","name":"Glass"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"t":{"tag":"repeater:0","snbt":"{id:\"minecraft:repeater\",Count:1b,Damage:0s}","name":"Redstone Repeater"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"}}},{"id":"gallows","shape":["ViI","SRU","ipi"],"keys":{"V":{"tag":"glass:0","snbt":"{id:\"minecraft:glass\",Count:1b,Damage:0s}","name":"Glass"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"p":{"tag":"piston:0","snbt":"{id:\"minecraft:piston\",Count:1b,Damage:0s}","name":"Piston"}}},{"id":"watchtower","shape":["Vii","OrU","iiD"],"keys":{"V":{"tag":"glass:0","snbt":"{id:\"minecraft:glass\",Count:1b,Damage:0s}","name":"Glass"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"}}},{"id":"sunlance","shape":["iiQ","GrB","iDI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"B":{"tag":"blaze_rod:0","snbt":"{id:\"minecraft:blaze_rod\",Count:1b,Damage:0s}","name":"Blaze Rod"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"hexbreaker","shape":["iiQ","GrP","iDI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"P":{"tag":"ender_pearl:0","snbt":"{id:\"minecraft:ender_pearl\",Count:1b,Damage:0s}","name":"Ender Pearl"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"witchlight","shape":["iiQ","GrA","iDI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"A":{"tag":"glowstone_dust:0","snbt":"{id:\"minecraft:glowstone_dust\",Count:1b,Damage:0s}","name":"Glowstone Dust"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"shotgun","shape":["iiI","SRX","UUi"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"cyclops","shape":["iiI","SRU","iFU"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"}}},{"id":"bellringer","shape":["iII","SRU","iUI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"}}},{"id":"lockjaw","shape":["iII","SRU","ipI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"p":{"tag":"piston:0","snbt":"{id:\"minecraft:piston\",Count:1b,Damage:0s}","name":"Piston"}}},{"id":"choir","shape":["iii","SRU","iUD"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"}}},{"id":"ashfall","shape":["iiI","SRU","iUh"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"h":{"tag":"hopper:0","snbt":"{id:\"minecraft:hopper\",Count:1b,Damage:0s}","name":"Hopper"}}},{"id":"railgun","shape":["iiD","grX","iQI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"nullpoint","shape":["iiQ","GrD","iQI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"cenotaph","shape":["iii","OrD","iGI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"stormcoil","shape":["iiQ","grD","iRI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"pallbearer","shape":["iii","hrU","iDI"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"h":{"tag":"hopper:0","snbt":"{id:\"minecraft:hopper\",Count:1b,Damage:0s}","name":"Hopper"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"ironpsalm","shape":["iii","hrU","ipi"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"h":{"tag":"hopper:0","snbt":"{id:\"minecraft:hopper\",Count:1b,Damage:0s}","name":"Hopper"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"U":{"tag":"gunpowder:0","snbt":"{id:\"minecraft:gunpowder\",Count:1b,Damage:0s}","name":"Gunpowder"},"p":{"tag":"piston:0","snbt":"{id:\"minecraft:piston\",Count:1b,Damage:0s}","name":"Piston"}}},{"id":"trench_blade","shape":[".I.",".F.",".S."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"mono_katana","shape":[".D.",".Q.",".S."],"keys":{"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"gravespike","shape":[".I.",".K.",".S."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"K":{"tag":"bone:0","snbt":"{id:\"minecraft:bone\",Count:1b,Damage:0s}","name":"Bone"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"vesper_dagger","shape":[".L.",".F.",".S."],"keys":{"L":{"tag":"dye:4","snbt":"{id:\"minecraft:dye\",Count:1b,Damage:4s}","name":"Lapis Lazuli"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"cautery_sabre","shape":[".B.",".I.",".S."],"keys":{"B":{"tag":"blaze_rod:0","snbt":"{id:\"minecraft:blaze_rod\",Count:1b,Damage:0s}","name":"Blaze Rod"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"rebar_sword","shape":[".I.",".O.",".S."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"execution_sword","shape":[".D.",".O.",".S."],"keys":{"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"wardcleaver","shape":[".E.",".I.",".S."],"keys":{"E":{"tag":"emerald:0","snbt":"{id:\"minecraft:emerald\",Count:1b,Damage:0s}","name":"Emerald"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"shock_baton","shape":[".R.",".C.",".S."],"keys":{"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"C":{"tag":"coal:0","snbt":"{id:\"minecraft:coal\",Count:1b,Damage:0s}","name":"Coal"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"suture_sickle","shape":[".Q.",".T.",".S."],"keys":{"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"breacher_axe","shape":["IO.","IS.",".S."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"railpick","shape":["IQ.","IS.",".S."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"thermal_machete","shape":["BC.","BS.",".S."],"keys":{"B":{"tag":"blaze_rod:0","snbt":"{id:\"minecraft:blaze_rod\",Count:1b,Damage:0s}","name":"Blaze Rod"},"C":{"tag":"coal:0","snbt":"{id:\"minecraft:coal\",Count:1b,Damage:0s}","name":"Coal"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"ember_falchion","shape":["BG.","BS.",".S."],"keys":{"B":{"tag":"blaze_rod:0","snbt":"{id:\"minecraft:blaze_rod\",Count:1b,Damage:0s}","name":"Blaze Rod"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"gravity_maul","shape":["OL.","OS.",".S."],"keys":{"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"L":{"tag":"dye:4","snbt":"{id:\"minecraft:dye\",Count:1b,Damage:4s}","name":"Lapis Lazuli"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"tollhammer","shape":["iK.","iS.",".S."],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"K":{"tag":"bone:0","snbt":"{id:\"minecraft:bone\",Count:1b,Damage:0s}","name":"Bone"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"altar_mallet","shape":["QW.","QS.",".S."],"keys":{"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"W":{"tag":"prismarine_shard:0","snbt":"{id:\"minecraft:prismarine_shard\",Count:1b,Damage:0s}","name":"Prismarine Shard"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"sentinel_spear","shape":["..I",".F.","S.."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"pilgrim_lance","shape":["..G",".K.","S.."],"keys":{"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"K":{"tag":"bone:0","snbt":"{id:\"minecraft:bone\",Count:1b,Damage:0s}","name":"Bone"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"hollow_halberd","shape":["..D",".I.","S.."],"keys":{"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"mourning_glaive","shape":["..E",".Q.","S.."],"keys":{"E":{"tag":"emerald:0","snbt":"{id:\"minecraft:emerald\",Count:1b,Damage:0s}","name":"Emerald"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"reaper_scythe","shape":["IK.","..S","..S"],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"K":{"tag":"bone:0","snbt":"{id:\"minecraft:bone\",Count:1b,Damage:0s}","name":"Bone"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"ossuary_flail","shape":["IG.","..S","..S"],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"wire_whip","shape":["TF.","..S","..S"],"keys":{"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"F":{"tag":"flint:0","snbt":"{id:\"minecraft:flint\",Count:1b,Damage:0s}","name":"Flint"},"S":{"tag":"stick:0","snbt":"{id:\"minecraft:stick\",Count:1b,Damage:0s}","name":"Stick"}}},{"id":"bulwark_helmet","shape":["dXd","iOi","..."],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"}}},{"id":"bulwark_chestplate","shape":["i.i","dXd","gOg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"}}},{"id":"bulwark_leggings","shape":["dXd","iOi","g.g"],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"}}},{"id":"bulwark_boots","shape":["i.i","dOd","gXg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"}}},{"id":"ranger_helmet","shape":["dXd","ifi","..."],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"f":{"tag":"emerald_block:0","snbt":"{id:\"minecraft:emerald_block\",Count:1b,Damage:0s}","name":"Block of Emerald"}}},{"id":"ranger_chestplate","shape":["i.i","dXd","gfg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"f":{"tag":"emerald_block:0","snbt":"{id:\"minecraft:emerald_block\",Count:1b,Damage:0s}","name":"Block of Emerald"}}},{"id":"ranger_leggings","shape":["dXd","ifi","g.g"],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"f":{"tag":"emerald_block:0","snbt":"{id:\"minecraft:emerald_block\",Count:1b,Damage:0s}","name":"Block of Emerald"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"}}},{"id":"ranger_boots","shape":["i.i","dfd","gXg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"f":{"tag":"emerald_block:0","snbt":"{id:\"minecraft:emerald_block\",Count:1b,Damage:0s}","name":"Block of Emerald"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"}}},{"id":"spectre_helmet","shape":["dXd","iei","..."],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"e":{"tag":"ender_eye:0","snbt":"{id:\"minecraft:ender_eye\",Count:1b,Damage:0s}","name":"Eye of Ender"}}},{"id":"spectre_chestplate","shape":["i.i","dXd","geg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"e":{"tag":"ender_eye:0","snbt":"{id:\"minecraft:ender_eye\",Count:1b,Damage:0s}","name":"Eye of Ender"}}},{"id":"spectre_leggings","shape":["dXd","iei","g.g"],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"e":{"tag":"ender_eye:0","snbt":"{id:\"minecraft:ender_eye\",Count:1b,Damage:0s}","name":"Eye of Ender"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"}}},{"id":"spectre_boots","shape":["i.i","ded","gXg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"e":{"tag":"ender_eye:0","snbt":"{id:\"minecraft:ender_eye\",Count:1b,Damage:0s}","name":"Eye of Ender"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"}}},{"id":"hazmat_helmet","shape":["dXd","isi","..."],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"s":{"tag":"slime:0","snbt":"{id:\"minecraft:slime\",Count:1b,Damage:0s}","name":"Slime Block"}}},{"id":"hazmat_chestplate","shape":["i.i","dXd","gsg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"s":{"tag":"slime:0","snbt":"{id:\"minecraft:slime\",Count:1b,Damage:0s}","name":"Slime Block"}}},{"id":"hazmat_leggings","shape":["dXd","isi","g.g"],"keys":{"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"s":{"tag":"slime:0","snbt":"{id:\"minecraft:slime\",Count:1b,Damage:0s}","name":"Slime Block"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"}}},{"id":"hazmat_boots","shape":["i.i","dsd","gXg"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"d":{"tag":"diamond_block:0","snbt":"{id:\"minecraft:diamond_block\",Count:1b,Damage:0s}","name":"Block of Diamond"},"s":{"tag":"slime:0","snbt":"{id:\"minecraft:slime\",Count:1b,Damage:0s}","name":"Slime Block"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"},"X":{"tag":"nether_star:0","snbt":"{id:\"minecraft:nether_star\",Count:1b,Damage:0s}","name":"Nether Star"}}},{"id":"sentry_turret","shape":["III","IiI","IRI"],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"}}},{"id":"portal_gun","shape":["iQD","QrP","iQG"],"keys":{"i":{"tag":"iron_block:0","snbt":"{id:\"minecraft:iron_block\",Count:1b,Damage:0s}","name":"Block of Iron"},"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"D":{"tag":"diamond:0","snbt":"{id:\"minecraft:diamond\",Count:1b,Damage:0s}","name":"Diamond"},"r":{"tag":"redstone_block:0","snbt":"{id:\"minecraft:redstone_block\",Count:1b,Damage:0s}","name":"Block of Redstone"},"P":{"tag":"ender_pearl:0","snbt":"{id:\"minecraft:ender_pearl\",Count:1b,Damage:0s}","name":"Ender Pearl"},"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"}}},{"id":"alloy_plate","shape":["III","IOI","III"],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"O":{"tag":"obsidian:0","snbt":"{id:\"minecraft:obsidian\",Count:1b,Damage:0s}","name":"Obsidian"}}},{"id":"weapon_core","shape":[".Q.","QRQ",".Q."],"keys":{"Q":{"tag":"quartz:0","snbt":"{id:\"minecraft:quartz\",Count:1b,Damage:0s}","name":"Nether Quartz"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"}}},{"id":"power_cell","shape":[".I.","IRI",".A."],"keys":{"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"},"R":{"tag":"redstone:0","snbt":"{id:\"minecraft:redstone\",Count:1b,Damage:0s}","name":"Redstone"},"A":{"tag":"glowstone_dust:0","snbt":"{id:\"minecraft:glowstone_dust\",Count:1b,Damage:0s}","name":"Glowstone Dust"}}},{"id":"ballistic_fiber","shape":["TTT","THT","TTT"],"keys":{"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"H":{"tag":"leather:0","snbt":"{id:\"minecraft:leather\",Count:1b,Damage:0s}","name":"Leather"}}},{"id":"trauma_kit","shape":[".T.","TaT",".T."],"keys":{"T":{"tag":"string:0","snbt":"{id:\"minecraft:string\",Count:1b,Damage:0s}","name":"String"},"a":{"tag":"golden_apple:0","snbt":"{id:\"minecraft:golden_apple\",Count:1b,Damage:0s}","name":"Golden Apple"}}},{"id":"field_ration","shape":[".w.","wcw",".w."],"keys":{"w":{"tag":"wheat:0","snbt":"{id:\"minecraft:wheat\",Count:1b,Damage:0s}","name":"Wheat"},"c":{"tag":"clay_ball:0","snbt":"{id:\"minecraft:clay_ball\",Count:1b,Damage:0s}","name":"Clay"}}},{"id":"coolant_injector","shape":[".M.","MWM",".M."],"keys":{"M":{"tag":"slime_ball:0","snbt":"{id:\"minecraft:slime_ball\",Count:1b,Damage:0s}","name":"Slimeball"},"W":{"tag":"prismarine_shard:0","snbt":"{id:\"minecraft:prismarine_shard\",Count:1b,Damage:0s}","name":"Prismarine Shard"}}},{"id":"sanitized_flesh","shape":[".C.","CoC",".C."],"keys":{"C":{"tag":"coal:0","snbt":"{id:\"minecraft:coal\",Count:1b,Damage:0s}","name":"Coal"},"o":{"tag":"rotten_flesh:0","snbt":"{id:\"minecraft:rotten_flesh\",Count:1b,Damage:0s}","name":"Rotten Flesh"}}},{"id":"scrap","shape":[".N.","NIN",".N."],"keys":{"N":{"tag":"iron_nugget:0","snbt":"{id:\"minecraft:iron_nugget\",Count:1b,Damage:0s}","name":"Iron Nugget"},"I":{"tag":"iron_ingot:0","snbt":"{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s}","name":"Iron Ingot"}}},{"id":"relic","shape":[".Y.","YeY",".Y."],"keys":{"Y":{"tag":"prismarine_crystals:0","snbt":"{id:\"minecraft:prismarine_crystals\",Count:1b,Damage:0s}","name":"Prismarine Crystals"},"e":{"tag":"ender_eye:0","snbt":"{id:\"minecraft:ender_eye\",Count:1b,Damage:0s}","name":"Eye of Ender"}}},{"id":"expedition_trophy","shape":[".G.","GgG",".G."],"keys":{"G":{"tag":"gold_ingot:0","snbt":"{id:\"minecraft:gold_ingot\",Count:1b,Damage:0s}","name":"Gold Ingot"},"g":{"tag":"gold_block:0","snbt":"{id:\"minecraft:gold_block\",Count:1b,Damage:0s}","name":"Block of Gold"}}},{"id":"guide","shape":[".J.","JbJ",".o."],"keys":{"J":{"tag":"paper:0","snbt":"{id:\"minecraft:paper\",Count:1b,Damage:0s}","name":"Paper"},"b":{"tag":"book:0","snbt":"{id:\"minecraft:book\",Count:1b,Damage:0s}","name":"Book"},"o":{"tag":"rotten_flesh:0","snbt":"{id:\"minecraft:rotten_flesh\",Count:1b,Damage:0s}","name":"Rotten Flesh"}}}];

var JasprRecipeBook = (function () {
  "use strict";

  var ITEM_SIZE = 20, ITEM_LIFT = 5, HEADER_GAP = 30;
  var books = [], disabled = false, prepared = null, failure = null;

  function die(where, error) {
    if (disabled) return null;
    disabled = true;
    failure = where + ": " + error;
    try {
      if ($rt_globals.console && $rt_globals.console.warn)
        $rt_globals.console.warn("[JasperCraft recipe book] disabled -- " + failure);
    } catch (ignored) { }
    return null;
  }

  // ---- stack helpers -------------------------------------------------------
  // ItemStack is a plain object here: PD is the count, bg_ the empty flag, rA the item,
  // bK the damage and bV the NBT tag. Reading them directly avoids a suspending call in
  // code that runs every frame.
  function empty(stack) {
    return !stack || stack === Ktg || stack.rA === null || stack.rA === undefined || !!stack.bg_;
  }
  function count(stack) { return empty(stack) ? 0 : (stack.PD | 0); }
  function tagged(stack) { return !empty(stack) && stack.bV !== null && stack.bV !== undefined; }
  function same(a, b) {
    return !empty(a) && !empty(b) && a.rA === b.rA && (a.bK | 0) === (b.bK | 0);
  }

  // ---- state ---------------------------------------------------------------
  function of(gui) {
    try {
      if (disabled) return null;
      for (var i = 0; i < books.length; i++) if (books[i].gui === gui) return books[i];
      return null;
    } catch (error) { return null; }
  }

  function attach(gui, firstCraft, grid, resultSlot, firstInv) {
    try {
      if (disabled) return;
      var book = of(gui);
      if (!book) {
        book = {gui: gui};
        books.push(book);
        if (books.length > 4) books.shift();   // only the open screen matters
      }
      book.firstCraft = firstCraft; book.grid = grid;
      book.resultSlot = resultSlot; book.firstInv = firstInv;
      book.scroll = 0; book.search = ""; book.focused = false;
      book.inventory = null; book.craftable = null; book.hover = null;
      book.refreshAt = 0; book.plan = null; book.caret = 0;
      layout(book);
    } catch (error) { die("attach", error); }
  }

  // The original picks eight columns and slides left of the window, shrinking the column
  // count when the window is too narrow to fit them. Kept as-is: it is what makes the panel
  // survive a small window, which matters on a laptop or a phone.
  function layout(book) {
    var gui = book.gui, perRow = 8;
    var offset = -ITEM_SIZE * perRow - 10;
    if (offset + (gui.is | 0) < 0) {
      perRow = Math.max(1, ((gui.is | 0) - 10) / ITEM_SIZE | 0);
      offset = -ITEM_SIZE * perRow - 10;
    }
    book.perRow = perRow;
    book.xOffset = offset;
    book.textBoxSize = -offset - 15;
  }

  function slots(book) { return book.gui.h2.cn; }
  function slotAt(book, index) {
    try {
      var list = slots(book);
      if (!list || !list.qN || index < 0 || index >= (list.g | 0)) return null;
      return list.qN.data[index];
    } catch (error) { return null; }
  }

  return {
    ITEM_SIZE: ITEM_SIZE, ITEM_LIFT: ITEM_LIFT, HEADER_GAP: HEADER_GAP,
    of: of, attach: attach, layout: layout, die: die,
    empty: empty, count: count, tagged: tagged, same: same,
    slotAt: slotAt,
    books: function () { return books; },
    disabled: function () { return disabled; },
    failure: function () { return failure; },
    prepared: function () { return prepared; },
    setPrepared: function (value) { prepared = value; }
  };
}());

/* The engine: everything that is pure logic lives here, in ordinary JavaScript, so that the
 * state machines further down never have to do more than walk a list somebody else built. */
(function () {
  "use strict";
  var RB = JasprRecipeBook;

  var LABEL = {
    gun: "Firearms", melee: "Melee", armor: "Exoskeletons", gadget: "Gadgets",
    block: "Blocks", material: "Materials", consumable: "Supplies",
    supply: "Supplies", artifact: "Relics"
  };

  function catalogue() {
    return typeof JasprCreativeCatalog !== "undefined" ? JasprCreativeCatalog : [];
  }

  // id -> creative-catalogue entry, which is where the display item comes from.
  var byId = null;
  function entry(id) {
    if (byId === null) {
      byId = {};
      var all = catalogue();
      for (var i = 0; i < all.length; i++) byId[all[i].id] = all[i];
    }
    return byId[id] || null;
  }

  RB.category = function (id) {
    var e = entry(id);
    var key = e ? e.category : "other";
    return LABEL[key] || "Other";
  };

  RB.title = function (id) {
    var e = entry(id);
    return e ? e.title : id;
  };

  /* What the recipes need, as SNBT, so the prepare pass can build one display stack for each
   * distinct ingredient once instead of every frame. */
  RB.ingredientKeys = function () {
    var seen = {}, out = [];
    for (var i = 0; i < JasprBlueprintTable.length; i++) {
      var keys = JasprBlueprintTable[i].keys;
      for (var letter in keys) {
        if (!Object.prototype.hasOwnProperty.call(keys, letter)) continue;
        var key = keys[letter];
        if (seen[key.tag]) continue;
        seen[key.tag] = true;
        out.push(key);
      }
    }
    return out;
  };

  RB.tagOf = function (blueprint, letter) { return blueprint.keys[letter].tag; };
  RB.nameOf = function (blueprint, letter) { return blueprint.keys[letter].name; };

  /* Which recipes the inventory can currently pay for.
   *
   * The original walks each ingredient slot by slot and reserves as it goes. Every recipe in
   * this table asks for one of a thing per cell, so counting demand per ingredient and
   * comparing totals is the same answer and far cheaper to do once a second.
   *
   * A stack carrying NBT is never spent, matching the original's canActAsIngredient. That is
   * not a detail here: Military Salvage is an iron nugget and a named gun is a diamond hoe,
   * so without it the book would happily feed somebody's rifle into a recipe. */
  RB.computeCraftable = function (book) {
   try {
    var prepared = RB.prepared();
    if (!prepared || !book.inventory) return;
    var have = {};
    for (var s = 0; s < book.inventory.length; s++) {
      var stack = book.inventory[s];
      if (RB.empty(stack) || RB.tagged(stack)) continue;
      for (var t = 0; t < prepared.list.length; t++) {
        var ing = prepared.list[t];
        if (RB.same(stack, prepared.stacks[ing.tag])) {
          have[ing.tag] = (have[ing.tag] || 0) + RB.count(stack);
          break;
        }
      }
    }
    var groups = {}, order = [];
    for (var i = 0; i < JasprBlueprintTable.length; i++) {
      var bp = JasprBlueprintTable[i];
      var need = {}, ok = true;
      for (var r = 0; r < bp.shape.length; r++) {
        var row = bp.shape[r];
        for (var c = 0; c < row.length; c++) {
          var letter = row.charAt(c);
          if (letter === ".") continue;
          var tag = RB.tagOf(bp, letter);
          need[tag] = (need[tag] || 0) + 1;
        }
      }
      for (var key in need) {
        if (!Object.prototype.hasOwnProperty.call(need, key)) continue;
        if ((have[key] || 0) < need[key]) { ok = false; break; }
      }
      if (!ok) continue;
      if (!prepared.outputs[bp.id]) continue;
      var name = RB.category(bp.id);
      if (!groups[name]) { groups[name] = []; order.push(name); }
      groups[name].push(bp);
    }
    order.sort();
    for (var g = 0; g < order.length; g++) {
      groups[order[g]].sort(function (a, b) {
        var x = RB.title(a.id).toLowerCase(), y = RB.title(b.id).toLowerCase();
        return x < y ? -1 : x > y ? 1 : 0;
      });
    }
    book.craftable = {order: order, groups: groups};
   } catch (error) { book.craftable = null; RB.die("craftable", error); }
  };

  RB.matches = function (book, bp) {
    var term = (book.search || "").toLowerCase();
    if (term.length < 2) return false;
    return RB.title(bp.id).toLowerCase().indexOf(term) >= 0 || bp.id.indexOf(term) >= 0;
  };
}());

/* Layout, hit-testing and the click script. Still ordinary JavaScript: the result is two
 * flat lists -- things to draw, and clicks to send -- that a state machine walks. */
(function () {
  "use strict";
  var RB = JasprRecipeBook;
  var SIZE = RB.ITEM_SIZE, LIFT = RB.ITEM_LIFT;

  // Called from inside a state machine, where a thrown error would take the whole screen
  // with it rather than just the panel. Nothing in here is allowed to escape.
  RB.plan = function (book, mouseX, mouseY) {
   try {
    var prepared = RB.prepared();
    if (!prepared || !book.craftable) return null;
    var gui = book.gui;
    var height = gui.gx | 0, xOffset = book.xOffset;
    var texts = [], items = [], rects = [];
    var hover = null;

    var search = [];
    if ((book.search || "").length >= 2) {
      for (var i = 0; i < JasprBlueprintTable.length; i++)
        if (RB.matches(book, JasprBlueprintTable[i]) && prepared.outputs[JasprBlueprintTable[i].id])
          search.push(JasprBlueprintTable[i]);
    }

    var rows = Math.ceil(search.length / book.perRow);
    for (var g = 0; g < book.craftable.order.length; g++)
      rows += 1 + Math.ceil(book.craftable.groups[book.craftable.order[g]].length / book.perRow);
    var needed = rows * SIZE;

    var y = 0, scrollable = false;
    if (needed > height) {
      y -= ((needed - height) / 2) | 0;
      if (y < -(gui.l7 | 0)) {
        y = -(gui.l7 | 0);
        scrollable = true;
        rects.push({x: xOffset, y: y, w: SIZE, h: SIZE, color: 0x60000000});
        rects.push({x: xOffset + book.textBoxSize - SIZE, y: y, w: SIZE, h: SIZE, color: 0x60000000});
        texts.push({s: "▲", x: xOffset + 6, y: y + 6, color: 0xFFFFAA});
        texts.push({s: "▼", x: xOffset + book.textBoxSize - SIZE + 6, y: y + 6, color: 0xFFFFAA});
        y += SIZE;
      }
    }
    book.scrollable = scrollable;
    if (!scrollable) book.scroll = 0;

    // Search box, drawn rather than borrowed: a native text field is several more suspending
    // calls per frame for a rectangle, a caret and a string.
    book.searchBox = {x: xOffset, y: y, w: book.textBoxSize, h: SIZE};
    rects.push({x: xOffset, y: y, w: book.textBoxSize, h: SIZE, color: book.focused ? 0xFF303030 : 0xFF1A1A1A});
    rects.push({x: xOffset + 1, y: y + 1, w: book.textBoxSize - 2, h: SIZE - 2, color: 0xFF000000});
    var shown = book.search || (book.focused ? "" : "Search…");
    if (book.focused && ((Date.now() / 500) | 0) % 2 === 0) shown += "_";
    texts.push({s: shown, x: xOffset + 4, y: y + 6, color: book.search ? 0xFFFFFF : 0x808080});

    var minY = y + RB.HEADER_GAP;
    book.minY = minY;
    y = minY - book.scroll * SIZE;

    function grid(list) {
      var x = 0;
      for (var n = 0; n < list.length; n++) {
        var bp = list[n];
        if (y >= minY) {
          items.push({stack: prepared.outputs[bp.id], x: xOffset + x, y: y - LIFT, id: bp.id});
          if (mouseX >= xOffset + x && mouseX < xOffset + x + SIZE &&
              mouseY >= y - LIFT && mouseY < y - LIFT + SIZE) hover = bp;
        }
        x += SIZE;
        if (x >= SIZE * book.perRow) { x = 0; y += SIZE; }
      }
      if (x !== 0) y += SIZE;
    }

    if (search.length) {
      if (y >= minY) texts.push({s: "Search results", x: xOffset, y: y, color: 0x55FFFF});
      y += SIZE;
      grid(search);
    }
    for (var k = 0; k < book.craftable.order.length; k++) {
      var name = book.craftable.order[k];
      if (y >= minY) texts.push({s: name, x: xOffset, y: y, color: 0xFFFF00});
      y += SIZE;
      grid(book.craftable.groups[name]);
    }

    book.hover = hover;
    if (hover) {
      texts.push({s: RB.title(hover.id), x: 0, y: height + 3, color: 0xFFFF00});
      for (var r = 0; r < hover.shape.length; r++) {
        for (var c = 0; c < hover.shape[r].length; c++) {
          var letter = hover.shape[r].charAt(c);
          if (letter === ".") continue;
          var stack = prepared.stacks[RB.tagOf(hover, letter)];
          if (stack) items.push({stack: stack, x: SIZE * c, y: height + 20 + SIZE * r, id: null});
        }
      }
    }
    return {rects: rects, texts: texts, items: items};
   } catch (error) { RB.die("plan", error); return null; }
  };

  RB.scrollBy = function (book, ticks) {
    if (!book || !book.scrollable) return;
    if (ticks < 0) book.scroll++;
    else if (ticks > 0 && book.scroll > 0) book.scroll--;
    if (book.scroll < 0) book.scroll = 0;
    if (book.scroll > 512) book.scroll = 512;
  };

  /* The click script.
   *
   * The original picks a stack up, taps it into each cell and puts the remainder back. That
   * idiom is kept, but grouped by ingredient rather than by cell: a recipe wanting five iron
   * becomes one pickup, five taps and one put-back instead of five of each. The final
   * shift-click on the result is what actually crafts, and the server decides the outcome --
   * nothing here asserts what comes out. */
  RB.planClicks = function (book, localX, localY, button) {
   try {
    var prepared = RB.prepared();
    if (!prepared) return null;

    if (book.searchBox) {
      var s = book.searchBox;
      if (localX >= s.x && localX < s.x + s.w && localY >= s.y && localY < s.y + s.h) {
        if (book.scrollable && localX < s.x + SIZE) { RB.scrollBy(book, -1); return []; }
        if (book.scrollable && localX >= s.x + s.w - SIZE) { RB.scrollBy(book, 1); return []; }
        book.focused = true;
        return [];
      }
      book.focused = false;
    }

    var bp = book.hover;
    if (!bp) return null;

    for (var g = 0; g < book.grid * book.grid; g++) {
      var slot = RB.slotAt(book, g + book.firstCraft);
      if (slot && book.craftStacks && !RB.empty(book.craftStacks[g])) return [];
    }

    var wanted = {};
    for (var r = 0; r < bp.shape.length; r++) {
      for (var c = 0; c < bp.shape[r].length; c++) {
        var letter = bp.shape[r].charAt(c);
        if (letter === ".") continue;
        var tag = RB.tagOf(bp, letter);
        if (!wanted[tag]) wanted[tag] = [];
        wanted[tag].push(book.firstCraft + r * book.grid + c);
      }
    }

    var clicks = [];
    for (var tag in wanted) {
      if (!Object.prototype.hasOwnProperty.call(wanted, tag)) continue;
      var targets = wanted[tag], sample = prepared.stacks[tag];
      var source = -1;
      for (var i = 0; i < book.inventory.length; i++) {
        var stack = book.inventory[i];
        if (RB.empty(stack) || RB.tagged(stack)) continue;
        if (RB.same(stack, sample) && RB.count(stack) >= targets.length) { source = i + book.firstInv; break; }
      }
      if (source < 0) return [];
      clicks.push([source, 0, "pickup"]);
      for (var t = 0; t < targets.length; t++) clicks.push([targets[t], 1, "pickup"]);
      clicks.push([source, 0, "pickup"]);
      if (clicks.length > 400) return [];
    }
    if (button === 0) clicks.push([book.resultSlot, 0, "quick"]);
    book.refreshAt = Date.now() + 300;
    return clicks;
   } catch (error) { RB.die("clicks", error); return null; }
  };

  RB.key = function (book, ch, code) {
    if (!book || !book.focused) return 0;
    if (code === 1) { book.focused = false; return 1; }          // escape leaves the box
    if (ch === 8) {                                              // backspace
      book.search = (book.search || "").slice(0, -1);
      return 1;
    }
    if (ch === 13 || ch === 10) { book.focused = false; return 1; }
    if (ch >= 32 && ch < 127 && (book.search || "").length < 24) {
      book.search = (book.search || "") + String.fromCharCode(ch);
      return 1;
    }
    return 1;   // while the box has focus it eats the key rather than closing the screen
  };
}());

/* The suspending side.
 *
 * Building an ItemStack from SNBT, reading a slot, drawing an item and clicking a slot are
 * all suspending calls in this client, so each of these is a TeaVM state machine with its
 * own saved state per call, exactly like the other native adapters in this pack. They are
 * kept deliberately dull -- every one of them just walks a list that the plain JavaScript
 * above already worked out. */

var JasprRecipeBookCache = null;
var JasprRecipeBookHandled = false;

/* One display ItemStack per recipe output and per distinct ingredient, built once. */
function JasprRecipeBookPrepare(a) {
  var b,c,d,e,f,$p=0,$z;
  if (FX()) { var $T=Ds(); $p=$T.l(); f=$T.l(); e=$T.l(); d=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (JasprRecipeBookCache !== null || JasprRecipeBook.disabled()) return;
      b = {outputs: {}, stacks: {}, list: JasprRecipeBook.ingredientKeys()};
      c = 0;
      $p = 1;
    case 1:
      if (c >= JasprBlueprintTable.length) { c = 0; $p = 4; continue _; }
      d = JasprRecipeBookSnbt(JasprBlueprintTable[c].id);
      if (d === null) { c = c + 1 | 0; $p = 1; continue _; }
      e = $rt_str(d);
      f = new Bk;
      $p = 2;
    case 2:
      $z = E0F(e); if (B()) break _;
      e = $z;
      $p = 3;
    case 3:
      BH8(f, e); if (B()) break _;
      b.outputs[JasprBlueprintTable[c].id] = f;
      c = c + 1 | 0;
      $p = 1;
      continue _;
    case 4:
      if (c >= b.list.length) { JasprRecipeBookCache = b; JasprRecipeBook.setPrepared(b); return; }
      e = $rt_str(b.list[c].snbt);
      f = new Bk;
      $p = 5;
    case 5:
      $z = E0F(e); if (B()) break _;
      e = $z;
      $p = 6;
    case 6:
      BH8(f, e); if (B()) break _;
      b.stacks[b.list[c].tag] = f;
      c = c + 1 | 0;
      $p = 4;
      continue _;
    default: FT();
  } }
  Ds().s(a,b,c,d,e,f,$p);
}

/* The 36 inventory slots plus the crafting grid, copied into plain arrays. */
function JasprRecipeBookScan(a, b) {
  var c,d,e,$p=0,$z;
  if (FX()) { var $T=Ds(); $p=$T.l(); e=$T.l(); d=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      b.inventory = [];
      b.craftStacks = [];
      c = 0;
      $p = 1;
    case 1:
      if (c >= 36) { c = 0; $p = 3; continue _; }
      d = JasprRecipeBook.slotAt(b, c + b.firstInv);
      if (d === null) { b.inventory.push(null); c = c + 1 | 0; $p = 1; continue _; }
      $p = 2;
    case 2:
      $z = d.eew(); if (B()) break _;
      b.inventory.push($z);
      c = c + 1 | 0;
      $p = 1;
      continue _;
    case 3:
      e = b.grid * b.grid | 0;
      if (c >= e) { JasprRecipeBook.computeCraftable(b); return; }
      d = JasprRecipeBook.slotAt(b, c + b.firstCraft);
      if (d === null) { b.craftStacks.push(null); c = c + 1 | 0; $p = 3; continue _; }
      $p = 4;
    case 4:
      $z = d.eew(); if (B()) break _;
      b.craftStacks.push($z);
      c = c + 1 | 0;
      $p = 3;
      continue _;
    default: FT();
  } }
  Ds().s(a,b,c,d,e,$p);
}

/* Draws the panel. Called from each screen's own foreground-layer method, which means the
 * matrix is already translated to the window's top-left corner -- that is what lets the
 * panel sit at a negative x, off to the left of the window, like the original. */
function JasprRecipeBookDraw(a, b, c) {
  var d,e,f,g,h,$p=0,$z;
  if (FX()) { var $T=Ds(); $p=$T.l(); h=$T.l(); g=$T.l(); f=$T.l(); e=$T.l(); d=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (JasprRecipeBook.disabled()) return;
      d = JasprRecipeBook.of(a);
      if (d === null) return;
      $p = 1;
    case 1:
      JasprRecipeBookPrepare(a); if (B()) break _;
      if (JasprRecipeBookCache === null) return;
      if (d.inventory !== null && Date.now() < d.refreshAt) { $p = 3; continue _; }
      $p = 2;
    case 2:
      JasprRecipeBookScan(a, d); if (B()) break _;
      d.refreshAt = Date.now() + 500;
      $p = 3;
    case 3:
      e = JasprRecipeBook.plan(d, (b | 0) - (a.is | 0), (c | 0) - (a.l7 | 0));
      if (e === null) return;
      d.lastPlan = e;
      f = 0;
      $p = 4;
    case 4:
      // Flat colour first: rectangles and text never suspend on their own account, but the
      // helpers are compiled async, so they get states too rather than being called raw.
      if (f >= e.rects.length) { f = 0; $p = 6; continue _; }
      g = e.rects[f];
      $p = 5;
    case 5:
      D49(g.x, g.y, g.x + g.w | 0, g.y + g.h | 0, g.color); if (B()) break _;
      f = f + 1 | 0;
      $p = 4;
      continue _;
    case 6:
      if (f >= e.items.length) { f = 0; $p = 9; continue _; }
      g = e.items[f];
      $p = 7;
    case 7:
      FkM(a.hu, a.j.v, g.stack, g.x, g.y); if (B()) break _;
      $p = 8;
    case 8:
      GcM(a.hu, a.J, g.stack, g.x, g.y, null); if (B()) break _;
      f = f + 1 | 0;
      $p = 6;
      continue _;
    case 9:
      if (f >= e.texts.length) return;
      g = e.texts[f];
      h = $rt_str(g.s);
      $p = 10;
    case 10:
      Efa(a.J, h, g.x, g.y, g.color); if (B()) break _;
      f = f + 1 | 0;
      $p = 9;
      continue _;
    default: FT();
  } }
  Ds().s(a,b,c,d,e,f,g,h,$p);
}

/* Sends the click script the plain JavaScript worked out. handleMouseClick is the same
 * entry point the screen uses for a real click, so the server sees ordinary slot traffic. */
function JasprRecipeBookClick(a, b, c, d) {
  var e,f,g,$p=0,$z;
  if (FX()) { var $T=Ds(); $p=$T.l(); g=$T.l(); f=$T.l(); e=$T.l(); d=$T.l(); c=$T.l(); b=$T.l(); a=$T.l(); }
  _:while (true) { switch ($p) {
    case 0:
      if (JasprRecipeBook.disabled()) return;
      e = JasprRecipeBook.of(a);
      if (e === null || e.inventory === null) return;
      f = JasprRecipeBook.planClicks(e, (b | 0) - (a.is | 0), (c | 0) - (a.l7 | 0), d | 0);
      // null means the click was not on the panel, so the screen must still see it.
      // An empty script means it was ours and there is simply nothing to send.
      JasprRecipeBookHandled = f !== null;
      if (f === null || f.length === 0) return;
      g = 0;
      $p = 1;
    case 1:
      MH(); if (B()) break _;
      $p = 2;
    case 2:
      if (g >= f.length) { e.inventory = null; e.refreshAt = 0; return; }
      $p = 3;
    case 3:
      FYl(a, null, f[g][0] | 0, f[g][1] | 0, f[g][2] === "quick" ? LyB : LyA); if (B()) break _;
      g = g + 1 | 0;
      $p = 2;
      continue _;
    default: FT();
  } }
  Ds().s(a,b,c,d,e,f,g,$p);
}

/* The creative catalogue already carries one SNBT blob per custom item, which is exactly the
 * display item this needs; no second copy of that data has to exist. */
function JasprRecipeBookSnbt(id) {
  try {
    if (typeof JasprCreativeCatalog === "undefined") return null;
    for (var i = 0; i < JasprCreativeCatalog.length; i++)
      if (JasprCreativeCatalog[i].id === id) return JasprCreativeCatalog[i].snbt;
    return null;
  } catch (error) { return null; }
}

/* Entry points the patched screen methods call. Each one swallows its own errors: a screen
 * that throws while drawing is a screen that cannot be closed. */
function JasprRecipeBookInit(a, firstCraft, grid, resultSlot, firstInv) {
  try { JasprRecipeBook.attach(a, firstCraft, grid, resultSlot, firstInv); }
  catch (error) { JasprRecipeBook.die("init", error); }
}

function JasprRecipeBookConsumedClick() {
  var handled = JasprRecipeBookHandled;
  JasprRecipeBookHandled = false;
  return handled ? 1 : 0;
}

function JasprRecipeBookKeyTyped(a, ch, code) {
  try {
    var book = JasprRecipeBook.of(a);
    if (!book || JasprRecipeBook.disabled()) return 0;
    return JasprRecipeBook.key(book, ch | 0, code | 0) ? 1 : 0;
  } catch (error) { JasprRecipeBook.die("key", error); return 0; }
}

/* The wheel. The original reads LWJGL's dWheel from handleMouseInput; in a browser the
 * event is right there, and listening once is cheaper than another patched method. */
(function () {
  try {
    if (typeof $rt_globals === "undefined" || !$rt_globals.document) return;
    $rt_globals.document.addEventListener("wheel", function (event) {
      try {
        if (JasprRecipeBook.disabled()) return;
        var all = JasprRecipeBook.books();
        if (!all.length) return;
        var book = all[all.length - 1];
        if (!book || !book.scrollable) return;
        JasprRecipeBook.scrollBy(book, event.deltaY > 0 ? -1 : 1);
      } catch (ignored) { }
    }, {passive: true});
  } catch (ignored) { }
}());

if (typeof window !== "undefined" && window) {
  try {
    window.JasprRecipeBookDiagnostics = Object.freeze({
      status: function () {
        var all = JasprRecipeBook.books(), book = all.length ? all[all.length - 1] : null;
        return {
          recipes: typeof JasprBlueprintTable !== "undefined" ? JasprBlueprintTable.length : 0,
          prepared: JasprRecipeBookCache !== null,
          disabled: JasprRecipeBook.disabled(),
          failure: JasprRecipeBook.failure(),
          open: !!book,
          craftable: book && book.craftable
            ? book.craftable.order.reduce(function (n, k) { return n + book.craftable.groups[k].length; }, 0)
            : 0,
          categories: book && book.craftable ? book.craftable.order : [],
          search: book ? book.search : null,
          scroll: book ? book.scroll : 0
        };
      }
    });
  } catch (error) { /* diagnostics are optional */ }
}
