package com.github.oowjzzoo.magicloot3;

import org.bukkit.Material;
import org.bukkit.block.Block;

public interface RuinHandler {

    void generate(Block b1, Block b2);

    void generate(Block b, Material type, byte data);
}
