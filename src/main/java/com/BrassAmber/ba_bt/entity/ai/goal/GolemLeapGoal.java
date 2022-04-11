package com.BrassAmber.ba_bt.entity.ai.goal;

import com.BrassAmber.ba_bt.entity.hostile.golem.BTAbstractGolem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class GolemLeapGoal extends Goal {
    private final BTAbstractGolem golem;
    private LivingEntity target;
    private final float jumpHeight;
    private final float minleap;
    private final float maxleap;
    private final float maxJump;

    public GolemLeapGoal(BTAbstractGolem golemIn, float jumpHeightIn, float maxJumpHeight, float minimumLeap, float maximumLeap) {
        this.golem = golemIn;
        this.jumpHeight = jumpHeightIn;
        this.minleap = minimumLeap;
        this.maxleap = maximumLeap;
        this.maxJump = maxJumpHeight;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (this.golem.isVehicle() || this.golem.isDormant()) {
            return false;
        } else {
            this.target = this.golem.getTarget();
            if (this.target == null) {
                return false;
            } else {
                double d0 = this.horizontalDistanceTo(this.target);
                double d1 = this.target.getY() - this.golem.getY();
                if (!(d0 < this.minleap) && !(d0 > this.maxleap) && !(d1 > this.maxJump) && !(d1 < 0)) {
                    if (!this.golem.isOnGround()) {
                        return false;
                    } else {
                        return this.golem.getRandom().nextInt(reducedTickDelay(5)) == 0;
                    }
                } else {
                    return false;
                }
            }
        }
    }

    public double horizontalDistanceTo (LivingEntity entity) {
        double xDistance = Math.abs(this.golem.getX() - entity.getX());
        double zDistance = Math.abs(this.golem.getZ() -  entity.getZ());

        return Math.sqrt((xDistance * xDistance) + (zDistance * zDistance));
    }

    public boolean canContinueToUse() {
        return !this.golem.isOnGround() && this.golem.isAwake();
    }

    public void start() {
        Vec3 vec3 = this.golem.getDeltaMovement();
        Vec3 vec31 = new Vec3(this.target.getX() - this.golem.getX(), this.target.getY() - this.golem.getY(), this.target.getZ() - this.golem.getZ());
        if (vec31.lengthSqr() > 1.0E-7D) {
            vec31 = vec31.normalize().scale(0.9D).add(vec3.scale(0.2D));
        }

        this.golem.setDeltaMovement(vec31.x, vec31.y, vec31.z);
    }
}