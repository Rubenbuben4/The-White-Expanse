package net.icegiant.client;

import net.icegiant.entity.FrostGoatEntity;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Frost Goat model: vanilla's goat shape 1:1 (its parts come from GoatModel.createBodyLayer, see ClientSetup), both
 * horns, legs and head moving like any four-legged animal. The renderer draws it twice as big.
 */
public class FrostGoatModel extends QuadrupedModel<FrostGoatEntity> {

    public FrostGoatModel(ModelPart root) {
        super(root, true, 19.0F, 1.0F, 2.5F, 2.0F, 24);
    }
}
