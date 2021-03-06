package gay.lemmaeof.resonancecannon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

public class ResonanceCannonItem extends ProjectileWeaponItem {

	public ResonanceCannonItem(Properties properties) {
		super(properties);
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return stack -> stack.getItem() == Items.ECHO_SHARD;
	}

	@Override
	public int getDefaultProjectileRange() {
		return 0;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack itemStack = user.getItemInHand(hand);
		boolean bl = !user.getProjectile(itemStack).isEmpty();
		if (!user.getAbilities().instabuild && !bl) {
			return InteractionResultHolder.fail(itemStack);
		} else {
			user.startUsingItem(hand);
			world.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 1.0F);
			return InteractionResultHolder.consume(itemStack);
		}
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		//TODO: custom rendering for use animation and such, this looks *awful*
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 40;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
		Vec3 view = user.getViewVector(1);
		Vec3 eye = user.getEyePosition();
		Vec3 limit = eye.add(view.x * 20, view.y * 20, view.z * 20);
		EntityHitResult result = ProjectileUtil.getEntityHitResult(user, eye, limit, new AABB(eye, limit), EntitySelector.LIVING_ENTITY_STILL_ALIVE, 0f);
		if (user instanceof Player player) {
			boolean bypassAmmo = player.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;
			ItemStack shardStack = player.getProjectile(stack);
			if (bypassAmmo && shardStack.isEmpty()) {
				shardStack = new ItemStack(Items.ECHO_SHARD);
			}

			if (world instanceof ServerLevel sworld && !shardStack.isEmpty()) {
				if (result != null && result.getEntity() instanceof LivingEntity target) {
					Vec3 startPos = user.position().add(0.0, 1.6F, 0.0);
					Vec3 midpoint = target.getEyePosition().subtract(startPos);
					Vec3 midpointNorm = midpoint.normalize();

					for (int i = 1; i < Mth.floor(midpoint.length()) + 7; ++i) {
						Vec3 particlePos = startPos.add(midpointNorm.scale(i));
						sworld.sendParticles(ParticleTypes.SONIC_BOOM, particlePos.x, particlePos.y, particlePos.z, 1, 0.0, 0.0, 0.0, 0.0);
					}

					world.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.0F);

					AABB aabb = target.getBoundingBox().inflate(1);
					List<Entity> entities = world.getEntities((Entity)null, aabb, EntitySelector.LIVING_ENTITY_STILL_ALIVE);
					for (Entity e : entities) {
						if (e instanceof LivingEntity living) {
							living.hurt(DamageSource.sonicBoom(user), entities.size() < 4? 10.0F : 5.0F);
							double verticalKnockback = 0.5 * (1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
							double horizontalKnockback = 2.5 * (1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
							living.push(midpointNorm.x() * horizontalKnockback, midpointNorm.y() * verticalKnockback, midpointNorm.z() * horizontalKnockback);
						}
					}
					world.gameEvent(user, GameEvent.ENTITY_ROAR, user.position());
				} else {
					world.playSound(null, user.blockPosition(), SoundEvents.WARDEN_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
				}

				player.getCooldowns().addCooldown(this, 60);
				if (!bypassAmmo && !player.getAbilities().instabuild) {
					shardStack.shrink(1);
					if (shardStack.isEmpty()) {
						player.getInventory().removeItem(shardStack);
					}
				}
				player.awardStat(Stats.ITEM_USED.get(this));
			}
		}

		return super.finishUsingItem(stack, world, user);
	}
}
