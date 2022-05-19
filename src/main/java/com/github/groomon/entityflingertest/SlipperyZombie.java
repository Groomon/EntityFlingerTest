package com.github.groomon.entityflingertest;

import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.SpigotTimings;
import org.bukkit.entity.Vehicle;

import java.util.Objects;
import java.util.stream.Stream;

public class SlipperyZombie extends EntityZombie {

    private boolean frictionDisabled = false;

    private float amEntity;
    private float anEntity;

    public SlipperyZombie(World world) {
        super(world);
        this.amEntity = 1.0F;
        this.anEntity = 1.0F;
    }

    public SlipperyZombie(Location loc) {
        super(((CraftWorld) Objects.requireNonNull(loc.getWorld())).getHandle());
        this.setPosition(loc.getX(), loc.getY(), loc.getZ());
        this.amEntity = 1.0F;
        this.anEntity = 1.0F;
    }

    @Override
    public void move(EnumMoveType enummovetype, Vec3D vec3d) {
        SpigotTimings.entityMoveTimer.startTiming();
        if (this.noclip) {
            this.a(this.getBoundingBox().c(vec3d));
            this.recalcPosition();
        } else {
            if (enummovetype == EnumMoveType.PISTON) {
                vec3d = this.b(vec3d);
                if (vec3d.equals(Vec3D.ORIGIN)) {
                    return;
                }
            }

            this.world.getMethodProfiler().enter("move");
            if (this.x.g() > 1.0E-7D) {
                vec3d = vec3d.h(this.x);
                this.x = Vec3D.ORIGIN;
                this.setMot(Vec3D.ORIGIN);
            }

            vec3d = this.a(vec3d, enummovetype);
            Vec3D vec3d1 = this.gEntity(vec3d); //fixed naming conflict
            if (vec3d1.g() > 1.0E-7D) {
                this.a(this.getBoundingBox().c(vec3d1));
                this.recalcPosition();
            }

            this.world.getMethodProfiler().exit();
            this.world.getMethodProfiler().enter("rest");
            this.positionChanged = !MathHelper.b(vec3d.x, vec3d1.x) || !MathHelper.b(vec3d.z, vec3d1.z);
            this.v = vec3d.y != vec3d1.y;

            //disable friction
            this.onGround = !frictionDisabled && this.v && vec3d.y < 0.0D;

            BlockPosition blockposition = this.ap();
            IBlockData iblockdata = this.world.getType(blockposition);
            this.a(vec3d1.y, this.onGround, iblockdata, blockposition);
            Vec3D vec3d2 = this.getMot();
            if (vec3d.x != vec3d1.x) {
                this.setMot(0.0D, vec3d2.y, vec3d2.z);
            }

            if (vec3d.z != vec3d1.z) {
                this.setMot(vec3d2.x, vec3d2.y, 0.0D);
            }

            net.minecraft.server.v1_16_R3.Block block = iblockdata.getBlock();
            if (vec3d.y != vec3d1.y) {
                block.a(this.world, this);
            }

            /*
            if (this.positionChanged && this.getBukkitEntity() instanceof Vehicle) {
                Vehicle vehicle = (Vehicle)this.getBukkitEntity();
                Block bl = this.world.getWorld().getBlockAt(MathHelper.floor(this.locX()), MathHelper.floor(this.locY()), MathHelper.floor(this.locZ()));
                if (vec3d.x > vec3d1.x) {
                    bl = bl.getRelative(BlockFace.EAST);
                } else if (vec3d.x < vec3d1.x) {
                    bl = bl.getRelative(BlockFace.WEST);
                } else if (vec3d.z > vec3d1.z) {
                    bl = bl.getRelative(BlockFace.SOUTH);
                } else if (vec3d.z < vec3d1.z) {
                    bl = bl.getRelative(BlockFace.NORTH);
                }

                if (!bl.getType().isAir()) {
                    VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, bl);
                    this.world.getServer().getPluginManager().callEvent(event);
                }
            }
            */

            if (this.onGround && !this.bv()) {
                block.stepOn(this.world, blockposition, this);
            }

            if (this.playStepSound() && !this.isPassenger()) {
                double d0 = vec3d1.x;
                double d1 = vec3d1.y;
                double d2 = vec3d1.z;
                if (!block.a(TagsBlock.CLIMBABLE)) {
                    d1 = 0.0D;
                }

                this.A = (float)((double)this.A + (double)MathHelper.sqrt(c(vec3d1)) * 0.6D);
                this.B = (float)((double)this.B + (double)MathHelper.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 0.6D);
                if (this.B > this.amEntity && !iblockdata.isAir()) {
                    this.amEntity = this.at();
                    if (!this.isInWater()) {
                        this.b(blockposition, iblockdata);
                    } else {
                        Entity entity = this.isVehicle() && this.getRidingPassenger() != null ? this.getRidingPassenger() : this;
                        float f = entity == this ? 0.35F : 0.4F;
                        Vec3D vec3d3 = entity.getMot();
                        float f1 = MathHelper.sqrt(vec3d3.x * vec3d3.x * 0.20000000298023224D + vec3d3.y * vec3d3.y + vec3d3.z * vec3d3.z * 0.20000000298023224D) * f;
                        if (f1 > 1.0F) {
                            f1 = 1.0F;
                        }

                        this.d(f1);
                    }
                } else if (this.B > this.anEntity && this.az() && iblockdata.isAir()) {
                    this.anEntity = this.e(this.B);
                }
            }

            try {
                this.checkBlockCollisions();
            } catch (Throwable var18) {
                CrashReport crashreport = CrashReport.a(var18, "Checking entity block collision");
                CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Entity being checked for collision");
                this.appendEntityCrashDetails(crashreportsystemdetails);
                throw new ReportedException(crashreport);
            }

            float f2 = this.getBlockSpeedFactor();
            this.setMot(this.getMot().d((double)f2, 1.0D, (double)f2));
            if (this.world.c(this.getBoundingBox().shrink(0.001D)).noneMatch((iblockdata1) -> {
                return iblockdata1.a(TagsBlock.FIRE) || iblockdata1.a(Blocks.LAVA);
            }) && this.fireTicks <= 0) {
                this.setFireTicks(-this.getMaxFireTicks());
            }

            if (this.aG() && this.isBurning()) {
                this.playSound(SoundEffects.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                this.setFireTicks(-this.getMaxFireTicks());
            }

            this.world.getMethodProfiler().exit();
        }

        SpigotTimings.entityMoveTimer.stopTiming();
    }

    //function naming conflict fixes

    private Vec3D gEntity(Vec3D vec3d) {
        AxisAlignedBB axisalignedbb = this.getBoundingBox();
        VoxelShapeCollision voxelshapecollision = VoxelShapeCollision.a(this);
        VoxelShape voxelshape = this.world.getWorldBorder().c();
        Stream<VoxelShape> stream = VoxelShapes.c(voxelshape, VoxelShapes.a(axisalignedbb.shrink(1.0E-7D)), OperatorBoolean.AND) ? Stream.empty() : Stream.of(voxelshape);
        Stream<VoxelShape> stream1 = this.world.c(this, axisalignedbb.b(vec3d), (entity) -> {
            return true;
        });
        StreamAccumulator<VoxelShape> streamaccumulator = new StreamAccumulator(Stream.concat(stream1, stream));
        Vec3D vec3d1 = vec3d.g() == 0.0D ? vec3d : a(this, vec3d, axisalignedbb, this.world, voxelshapecollision, streamaccumulator);
        boolean flag = vec3d.x != vec3d1.x;
        boolean flag1 = vec3d.y != vec3d1.y;
        boolean flag2 = vec3d.z != vec3d1.z;
        boolean flag3 = this.onGround || flag1 && vec3d.y < 0.0D;
        if (this.G > 0.0F && flag3 && (flag || flag2)) {
            Vec3D vec3d2 = a(this, new Vec3D(vec3d.x, (double)this.G, vec3d.z), axisalignedbb, this.world, voxelshapecollision, streamaccumulator);
            Vec3D vec3d3 = a(this, new Vec3D(0.0D, (double)this.G, 0.0D), axisalignedbb.b(vec3d.x, 0.0D, vec3d.z), this.world, voxelshapecollision, streamaccumulator);
            if (vec3d3.y < (double)this.G) {
                Vec3D vec3d4 = a(this, new Vec3D(vec3d.x, 0.0D, vec3d.z), axisalignedbb.c(vec3d3), this.world, voxelshapecollision, streamaccumulator).e(vec3d3);
                if (c(vec3d4) > c(vec3d2)) {
                    vec3d2 = vec3d4;
                }
            }

            if (c(vec3d2) > c(vec3d1)) {
                return vec3d2.e(a(this, new Vec3D(0.0D, -vec3d2.y + vec3d.y, 0.0D), axisalignedbb.c(vec3d2), this.world, voxelshapecollision, streamaccumulator));
            }
        }

        return vec3d1;
    }

    //additional logic
    public void disableFriction(boolean d) {
        this.frictionDisabled = d;
    }


}
