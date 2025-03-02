package moriyashiine.bewitchment.mixin.poppet;

import moriyashiine.bewitchment.api.BewitchmentAPI;
import moriyashiine.bewitchment.api.component.CursesComponent;
import moriyashiine.bewitchment.api.component.TransformationComponent;
import moriyashiine.bewitchment.api.event.ReviveEvents;
import moriyashiine.bewitchment.api.misc.PoppetData;
import moriyashiine.bewitchment.client.network.packet.SpawnSmokeParticlesPacket;
import moriyashiine.bewitchment.common.entity.component.AdditionalWaterDataComponent;
import moriyashiine.bewitchment.common.entity.component.AdditionalWerewolfDataComponent;
import moriyashiine.bewitchment.common.entity.living.VampireEntity;
import moriyashiine.bewitchment.common.entity.living.WerewolfEntity;
import moriyashiine.bewitchment.common.entity.living.util.BWHostileEntity;
import moriyashiine.bewitchment.common.registry.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("ConstantConditions")
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
	@Shadow
	public abstract boolean addStatusEffect(StatusEffectInstance effect);
	
	@Shadow
	public abstract boolean clearStatusEffects();
	
	@Shadow
	public abstract void setHealth(float health);
	
	@Shadow
	protected abstract float getSoundVolume();
	
	@Shadow
	public abstract float getSoundPitch();
	
	public LivingEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}
	
	@Inject(method = "tick", at = @At("TAIL"))
	private void tick(CallbackInfo callbackInfo) {
		if (!world.isClient) {
			AdditionalWaterDataComponent.maybeGet((LivingEntity) (Object) this).ifPresent(additionalWaterDataComponent -> {
				if (additionalWaterDataComponent.isSubmerged()) {
					additionalWaterDataComponent.setSubmerged(false);
				}
			});
		}
	}
	
	@ModifyVariable(method = "applyArmorToDamage", at = @At("HEAD"))
	private float modifyDamage(float amount, DamageSource source) {
		if (!world.isClient) {
			if (amount > 0 && (Object) this instanceof PlayerEntity player && !BewitchmentAPI.isVampire(this, true)) {
				PoppetData poppetData = BewitchmentAPI.getPoppetFromInventory(world, BWObjects.VAMPIRIC_POPPET, null, player.getInventory().main);
				if (!poppetData.stack.isEmpty()) {
					LivingEntity owner = BewitchmentAPI.getTaglockOwner(world, poppetData.stack);
					if (!BewitchmentAPI.isVampire(owner, true) && !getUuid().equals(owner.getUuid()) && owner.damage(BWDamageSources.VAMPIRE, amount)) {
						boolean sync = false;
						if (poppetData.stack.damage((int) (amount * (BewitchmentAPI.getFamiliar(player) == EntityType.WOLF && random.nextBoolean() ? 0.5f : 1)), random, null) && poppetData.stack.getDamage() >= poppetData.stack.getMaxDamage()) {
							poppetData.stack.decrement(1);
							sync = true;
						}
						poppetData.update(world, sync);
						return 0;
					}
				}
			}
			if (source.isFire() || source == DamageSource.DROWN || source == DamageSource.FALL || source == DamageSource.FLY_INTO_WALL) {
				PoppetData poppetData = BewitchmentAPI.getPoppet(world, BWObjects.PROTECTION_POPPET, this);
				if (!poppetData.stack.isEmpty()) {
					boolean sync = false;
					if (poppetData.stack.damage((int) (amount * ((Object) this instanceof PlayerEntity player && BewitchmentAPI.getFamiliar(player) == EntityType.WOLF && random.nextBoolean() ? 0.5f : 1)), random, null) && poppetData.stack.getDamage() >= poppetData.stack.getMaxDamage()) {
						poppetData.stack.decrement(1);
						sync = true;
					}
					poppetData.update(world, sync);
					return 0;
				}
			}
			if (source.getAttacker() instanceof LivingEntity livingAttacker && BewitchmentAPI.isWeakToSilver(livingAttacker)) {
				PoppetData poppetData = BewitchmentAPI.getPoppet(world, BWObjects.JUDGMENT_POPPET, this);
				if (!poppetData.stack.isEmpty()) {
					boolean sync = false;
					if (poppetData.stack.damage((Object) this instanceof PlayerEntity && BewitchmentAPI.getFamiliar((PlayerEntity) (Object) this) == EntityType.WOLF && random.nextBoolean() ? 0 : 1, random, null) && poppetData.stack.getDamage() >= poppetData.stack.getMaxDamage()) {
						poppetData.stack.decrement(1);
						sync = true;
					}
					poppetData.update(world, sync);
					amount /= 4;
				}
			}
			if (source.getSource() instanceof LivingEntity livingSource) {
				PoppetData poppetData = BewitchmentAPI.getPoppet(world, BWObjects.FATIGUE_POPPET, this);
				boolean sync = false;
				if (!poppetData.stack.isEmpty() && livingSource.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1)) && poppetData.stack.damage((Object) this instanceof PlayerEntity player && BewitchmentAPI.getFamiliar(player) == EntityType.WOLF && random.nextBoolean() ? 0 : 1, random, null) && poppetData.stack.getDamage() >= poppetData.stack.getMaxDamage()) {
					poppetData.stack.decrement(1);
					sync = true;
				}
				poppetData.update(world, sync);
			}
		}
		return amount;
	}
	
	@Inject(method = "tryUseTotem", at = @At("RETURN"), cancellable = true)
	private void tryUseTotem(DamageSource source, CallbackInfoReturnable<Boolean> callbackInfo) {
		if (!world.isClient) {
			if (!callbackInfo.getReturnValue()) {
				boolean isPlayer = (Object) this instanceof PlayerEntity;
				PoppetData poppetData = BewitchmentAPI.getPoppet(world, BWObjects.DEATH_PROTECTION_POPPET, this);
				if (!poppetData.stack.isEmpty() && !(isPlayer && ReviveEvents.CANCEL_REVIVE.invoker().shouldCancel((PlayerEntity) (Object) this, source, poppetData.stack))) {
					if (isPlayer) {
						ReviveEvents.ON_REVIVE.invoker().onRevive((PlayerEntity) (Object) this, source, poppetData.stack);
					}
					boolean sync = false;
					if (poppetData.stack.damage((Object) this instanceof PlayerEntity player && BewitchmentAPI.getFamiliar(player) == EntityType.WOLF && random.nextBoolean() ? 0 : 1, random, null) && poppetData.stack.getDamage() >= poppetData.stack.getMaxDamage()) {
						poppetData.stack.decrement(1);
						sync = true;
					}
					poppetData.update(world, sync);
					setHealth(1);
					clearStatusEffects();
					addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
					addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
					addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
					callbackInfo.setReturnValue(true);
				}
			}
			if (callbackInfo.getReturnValue() && (Object) this instanceof PlayerEntity player && CursesComponent.get(player).hasCurse(BWCurses.SUSCEPTIBILITY)) {
				TransformationComponent.maybeGet(player).ifPresent(transformationComponent -> {
					if (transformationComponent.getTransformation() == BWTransformations.HUMAN) {
						if (source.getSource() instanceof VampireEntity || (source.getSource() instanceof PlayerEntity playerSource && BewitchmentAPI.isVampire(playerSource, true) && BewitchmentAPI.isPledged(playerSource, BWPledges.LILITH))) {
							transformationComponent.getTransformation().onRemoved(player);
							transformationComponent.setTransformation(BWTransformations.VAMPIRE);
							transformationComponent.getTransformation().onAdded(player);
							PlayerLookup.tracking(this).forEach(trackingPlayer -> SpawnSmokeParticlesPacket.send(trackingPlayer, this));
							SpawnSmokeParticlesPacket.send(player, this);
							world.playSound(null, getBlockPos(), BWSoundEvents.ENTITY_GENERIC_CURSE, getSoundCategory(), getSoundVolume(), getSoundPitch());
						}
						else if (source.getSource() instanceof WerewolfEntity || (source.getSource() instanceof PlayerEntity playerSource && BewitchmentAPI.isWerewolf(playerSource, false) && BewitchmentAPI.isPledged(playerSource, BWPledges.HERNE))) {
							transformationComponent.getTransformation().onRemoved(player);
							transformationComponent.setTransformation(BWTransformations.WEREWOLF);
							transformationComponent.getTransformation().onAdded(player);
							int variant = -1;
							if (source.getSource() instanceof WerewolfEntity) {
								variant = source.getSource().getDataTracker().get(BWHostileEntity.VARIANT);
							}
							else if (source.getSource() instanceof PlayerEntity playerSource) {
								variant = AdditionalWerewolfDataComponent.get(playerSource).getVariant();
							}
							if (variant > -1) {
								AdditionalWerewolfDataComponent.get(player).setVariant(variant);
							}
							PlayerLookup.tracking(this).forEach(trackingPlayer -> SpawnSmokeParticlesPacket.send(trackingPlayer, this));
							SpawnSmokeParticlesPacket.send(player, this);
							world.playSound(null, getBlockPos(), BWSoundEvents.ENTITY_GENERIC_CURSE, getSoundCategory(), getSoundVolume(), getSoundPitch());
						}
					}
				});
			}
		}
	}
}
