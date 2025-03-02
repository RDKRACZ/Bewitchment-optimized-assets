package moriyashiine.bewitchment.common.item;

import moriyashiine.bewitchment.api.component.PledgeComponent;
import moriyashiine.bewitchment.api.entity.Pledgeable;
import moriyashiine.bewitchment.common.registry.BWPledges;
import moriyashiine.bewitchment.common.registry.BWSoundEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class GrotestqueStewItem extends Item {
	public GrotestqueStewItem(Settings settings) {
		super(settings);
	}
	
	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof PlayerEntity player) {
			PledgeComponent pledgeComponent = PledgeComponent.get(player);
			if (pledgeComponent.getPledge().equals(BWPledges.NONE)) {
				LivingEntity closest = null;
				for (LivingEntity livingEntity : world.getEntitiesByClass(LivingEntity.class, new Box(user.getBlockPos()).expand(8), foundEntity -> foundEntity.isAlive() && foundEntity instanceof Pledgeable)) {
					if (closest == null || user.distanceTo(livingEntity) < user.distanceTo(closest)) {
						closest = livingEntity;
					}
				}
				if (closest instanceof Pledgeable pledgeable) {
					pledgeComponent.setPledge(pledgeable.getPledgeID());
					pledgeable.getPledgedPlayerUUIDs().add(user.getUuid());
					world.playSound(null, user.getBlockPos(), BWSoundEvents.ENTITY_GENERIC_PLEDGE, SoundCategory.PLAYERS, 1, 1);
				}
			}
		}
		return Items.MUSHROOM_STEW.finishUsing(stack, world, user);
	}
}
