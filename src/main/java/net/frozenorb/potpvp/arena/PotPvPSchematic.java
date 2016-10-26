package net.frozenorb.potpvp.arena;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.frozenorb.potpvp.arena.schematic.SchematicUtil;
import net.frozenorb.potpvp.arena.schematic.WorldSchematic;

import java.io.File;

/**
 * Represents an arena schematic. See {@link net.frozenorb.potpvp.arena}
 * for a comparision of {@link Arena}s and {@link PotPvPSchematic}s.
 */
public final class PotPvPSchematic {
    private static final File SCHEMATICS_DIRECTORY = new File(new File(new File("plugins"), "WorldEdit"), "schematics");

    /**
     * Name of this schematic (ex "Candyland")
     */
    @Getter private String name;

    /**
     * Maximum number of players that can occupy an instance of this arena.
     * Some small schematics should only be used for smaller fights
     */
    @Getter private int maxPlayerCount;

    /**
     * Minimum number of players that can occupy an instance of this arena.
     * Some large schematics should only be used for larger fights
     */
    @Getter private int minPlayerCount;

    /**
     * If this schematic can be used for ranked matches
     * Some "joke" schematics cannot be used for ranked (due to their nature)
     */
    @Getter private boolean supportsRanked;

    /**
     * If this schematic can be only be used for archer matches
     * Some schematics are built for specifically archer fights
     */
    @Getter private boolean archerOnly;

    /**
     * It's index on the X axis on the grid. Set to 0 if not set yet
     *
     * @see ArenaGrid
     */
    @Getter @Setter(AccessLevel.PACKAGE) private int index = 0;

    /**
     * The amount of arenas to be used on the Z axis on the grid
     *
     * @see ArenaGrid
     */
    @Getter @Setter(AccessLevel.PACKAGE) private int copies = 0;

    public File getFile() {
        return new File(SCHEMATICS_DIRECTORY, name + ".schematic");
    }

    public WorldSchematic asWorldSchematic() throws Exception {
        return SchematicUtil.instance().load(getFile());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PotPvPSchematic && ((PotPvPSchematic) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}