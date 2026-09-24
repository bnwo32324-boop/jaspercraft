# Modding notes

The active browser client is Minecraft 1.12.2, but it is a compiled JavaScript distribution rather than a reproducible client-source workspace. The included `site\classes.js.map` lists 1.12.2 source paths (advancements, recipe book, protocol-340 packets, and related classes) but does not embed `sourcesContent`.

Recommended order for future custom work:

1. Put gameplay rules, commands, new server mechanics, AI-created systems, persistence, and administration into Paper/Bukkit plugins under `server\custom-plugins\`. This is the easiest code to build, test, remove, and version.
2. Use vanilla 1.12.2 resource packs for textures, sounds, models, and language changes when possible.
3. Change the browser client only when a feature truly requires rendering/UI/client behavior. First obtain a genuine buildable 1.12.2 TeaVM source tree that reproduces this distribution; do not use the mislabeled 1.8.8 workspace.
4. Do not drop Forge mods into this server or client. A Forge jar is not compatible with this browser runtime. Port the behavior into a Paper plugin or directly into a verified client source tree.
5. Back up `server\`, `site\`, and this whole project before large changes. Develop new plugins in separate source folders and keep generated jars under `server\plugins\`.

Security rule to preserve: `jasper` is the only allowed operator username, AuthMe protects it with a password, and `TestServerControl` blocks non-authentication commands from every other player. Review that rule whenever adding plugins that create alternate command channels or web administration endpoints.