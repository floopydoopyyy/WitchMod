DROP UGLY PLAYER SKINS IN THIS FOLDER.

  assets/witchmod/textures/entity/ugly/<name>.png

Any valid .png in here is picked up automatically - no code change, no registration, no
restart beyond a resource reload (F3+T). Delete one and it stops appearing.

  * Standard Minecraft player skin format, 64x64.

  * FILENAMES MUST BE LOWERCASE with no spaces. Minecraft itself refuses to load any
    resource whose path contains anything other than:

        a-z   0-9   _   -   .

    So "purple guy!.png" is silently ignored by the game before this mod ever sees it,
    and must be renamed to something like "purple_guy.png". Check the log on startup:
    it prints "[Ugly] Loaded N ugly skin(s)", and anything rejected shows up as
    "Invalid path in pack: ... ignoring".

  * A name ending in "_slim" (e.g. "gremlin_slim.png") is treated as an Alex/slim-armed
    skin. Anything else is treated as Steve/wide.

Which skin a cursed player gets is chosen from a number rolled by the SERVER, then taken
modulo the number of skins each client can see - so every client shows the same player the
same face, without the server needing to know anything about these files.

This readme is ignored (only .png files are listed).
