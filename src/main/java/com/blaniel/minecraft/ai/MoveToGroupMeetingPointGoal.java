package com.blaniel.minecraft.ai;

import com.blaniel.minecraft.entity.BlanielVillagerEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * AI Goal to move to the meeting point of a social group
 */
public class MoveToGroupMeetingPointGoal extends Goal {

    private final BlanielVillagerEntity entity;
    private Vec3d meetingPoint;
    private final double speed;
    private final double acceptableDistance;
    private int ticksStuck;
    private static final int MAX_STUCK_TICKS = 100; // 5 seconds

    public MoveToGroupMeetingPointGoal(BlanielVillagerEntity entity, double speed) {
        this.entity = entity;
        this.speed = speed;
        this.acceptableDistance = 2.0; // 2 blocks from center point
        this.ticksStuck = 0;

        // This goal doesn't block look or move
        this.setControls(EnumSet.of(Control.MOVE));
    }

    /**
     * Set meeting point
     */
    public void setMeetingPoint(Vec3d point) {
        this.meetingPoint = point;
        this.ticksStuck = 0;
    }

    /**
     * Clear meeting point
     */
    public void clearMeetingPoint() {
        this.meetingPoint = null;
        this.entity.getNavigation().stop();
    }

    /**
     * Can this goal start?
     */
    @Override
    public boolean canStart() {
        // Only if there is a meeting point and not in conversation
        if (this.meetingPoint == null || this.entity.isInConversation()) {
            return false;
        }

        // Only if far from the point
        double distance = this.entity.getPos().distanceTo(this.meetingPoint);
        return distance > this.acceptableDistance;
    }

    /**
     * Should it continue?
     */
    @Override
    public boolean shouldContinue() {
        if (this.meetingPoint == null || this.entity.isInConversation()) {
            return false;
        }

        // Check if arrived
        double distance = this.entity.getPos().distanceTo(this.meetingPoint);
        if (distance <= this.acceptableDistance) {
            return false; // Already arrived
        }

        // Check if stuck
        if (this.ticksStuck > MAX_STUCK_TICKS) {
            // Give up if stuck for too long
            return false;
        }

        return true;
    }

    /**
     * Start execution
     */
    @Override
    public void start() {
        if (this.meetingPoint != null) {
            // Navigate to meeting point
            this.entity.getNavigation().startMovingTo(
                this.meetingPoint.x,
                this.meetingPoint.y,
                this.meetingPoint.z,
                this.speed
            );
        }
    }

    /**
     * Update every tick
     */
    @Override
    public void tick() {
        if (this.meetingPoint == null) {
            return;
        }

        // Check if navigating
        if (this.entity.getNavigation().isIdle()) {
            // Retry navigation
            this.entity.getNavigation().startMovingTo(
                this.meetingPoint.x,
                this.meetingPoint.y,
                this.meetingPoint.z,
                this.speed
            );

            this.ticksStuck++;
        } else {
            // Is moving, reset counter
            this.ticksStuck = 0;
        }

        // Look at meeting point while moving
        this.entity.getLookControl().lookAt(
            this.meetingPoint.x,
            this.entity.getEyeY(),
            this.meetingPoint.z
        );
    }

    /**
     * Stop execution
     */
    @Override
    public void stop() {
        this.entity.getNavigation().stop();
    }
}
