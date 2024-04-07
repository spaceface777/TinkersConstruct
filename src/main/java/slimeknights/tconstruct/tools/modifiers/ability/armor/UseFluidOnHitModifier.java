package slimeknights.tconstruct.tools.modifiers.ability.armor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffectManager;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffects;
import slimeknights.tconstruct.library.modifiers.modules.fluid.TankModule;
import slimeknights.tconstruct.library.modifiers.util.ModifierHookMap.Builder;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.particle.FluidParticleData;

import javax.annotation.Nullable;

/** Modifier to handle spilling recipes onto self when attacked */
public abstract class UseFluidOnHitModifier extends Modifier {
  protected TankModule tank;

  @Override
  protected void registerHooks(Builder hookBuilder) {
    super.registerHooks(hookBuilder);
    tank = new TankModule(FluidType.BUCKET_VOLUME, true);
    hookBuilder.addModule(tank);
  }

  /** Spawns particles at the given entity */
  public static void spawnParticles(Entity target, FluidStack fluid) {
    if (target.level instanceof ServerLevel) {
      ((ServerLevel)target.level).sendParticles(new FluidParticleData(TinkerCommons.fluidParticle.get(), fluid), target.getX(), target.getY(0.5), target.getZ(), 10, 0.1, 0.2, 0.1, 0.2);
    }
  }

  /** Overridable method to create the attack context and spawn particles */
  public abstract FluidEffectContext.Entity createContext(LivingEntity self, @Nullable Player player, @Nullable Entity attacker);

  /**
   * Checks if the modifier triggers
   * TODO 1.19: can ditch this method in favor of just moving the logic to where calls {@link #useFluid(IToolStackView, ModifierEntry, EquipmentContext, EquipmentSlot, DamageSource, boolean)}, keeping just in case an addon uses.
   */
  protected abstract boolean doesTrigger(DamageSource source, boolean isDirectDamage);

  /** Logic for using the fluid */
  protected void useFluid(IToolStackView tool, ModifierEntry modifier, EquipmentContext context, EquipmentSlot slotType, DamageSource source, boolean isDirectDamage) {
    if (doesTrigger(source, isDirectDamage)) {
      // 25% chance of working per level, 50% per level on shields
      float level = modifier.getEffectiveLevel();
      if (RANDOM.nextInt(slotType.getType() == Type.HAND ? 2 : 4) < level) {
        FluidStack fluid = tank.getFluid(tool);
        if (!fluid.isEmpty()) {
          LivingEntity self = context.getEntity();
          Player player = self instanceof Player p ? p : null;
          FluidEffects recipe = FluidEffectManager.INSTANCE.find(fluid.getFluid());
          if (recipe.hasEffects()) {
            FluidEffectContext.Entity fluidContext = createContext(self, player, source.getEntity());
            int consumed = recipe.applyToEntity(fluid, level, fluidContext, FluidAction.EXECUTE);
            if (consumed > 0 && (player == null || !player.isCreative())) {
              spawnParticles(fluidContext.getTarget(), fluid);
              fluid.shrink(consumed);
              tank.setFluid(tool, fluid);
            }
          }
        }
      }
    }
  }
}
