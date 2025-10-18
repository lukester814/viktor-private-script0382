package com.plebsscripts.viktor.util;

import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.input.mouse.MouseSettings;
import org.dreambot.api.utilities.Sleep;

import java.awt.*;
import java.util.Random;

/**
 * SmartMouse v2 - Realistic human mouse movement algorithm
 * Based on Bezier curves with natural variation
 *
 * Features:
 * - Curved paths (not straight lines)
 * - Speed variation (fast -> slow near target)
 * - Micro-corrections
 * - Overshooting and correction
 * - Random pauses
 */
public class SmartMouse {
    private static final Random random = new Random();

    // Movement settings (per-account variance)
    private final double speedMultiplier;
    private final double accuracyFactor;
    private final boolean enableOvershoots;
    private final boolean enableMicroCorrections;

    public SmartMouse() {
        // Generate account-specific mouse characteristics
        this.speedMultiplier = 0.8 + (random.nextDouble() * 0.4); // 0.8x to 1.2x speed
        this.accuracyFactor = 0.85 + (random.nextDouble() * 0.15); // 85-100% accuracy
        this.enableOvershoots = random.nextInt(100) < 30; // 30% chance
        this.enableMicroCorrections = random.nextInt(100) < 60; // 60% chance

        Logs.debug("SmartMouse initialized: speed=" + String.format("%.2f", speedMultiplier) +
                ", accuracy=" + String.format("%.2f", accuracyFactor));
    }

    /**
     * Move mouse to target with human-like behavior
     * @param target Target point
     * @return true if successful
     */
    public boolean move(Point target) {
        if (target == null) return false;

        Point current = Mouse.getPosition();

        // Sometimes we're already close enough
        if (current.distance(target) < 5) {
            return true;
        }

        // Generate path using Bezier curve
        Point[] path = generateBezierPath(current, target);

        // Follow path with speed variation
        followPath(path, target);

        // Maybe overshoot and correct
        if (enableOvershoots && shouldOvershoot()) {
            performOvershoot(target);
        }

        // Micro-corrections near target
        if (enableMicroCorrections && Mouse.getPosition().distance(target) > 3) {
            performMicroCorrection(target);
        }

        return Mouse.getPosition().distance(target) < 10;
    }

    /**
     * Click with human-like behavior
     * @param target Click target
     * @param rightClick True for right-click
     * @return true if successful
     */
    public boolean click(Point target, boolean rightClick) {
        // Move to target
        if (!move(target)) {
            return false;
        }

        // Human reaction delay before clicking
        Sleep.sleep((int)(150 + random.nextGaussian() * 50));

        // Sometimes miss-click slightly (1% chance)
        if (random.nextInt(100) < 1) {
            Logs.debug("SmartMouse: Intentional miss-click");
            Point offset = new Point(
                    target.x + random.nextInt(10) - 5,
                    target.y + random.nextInt(10) - 5
            );
            Mouse.move(offset);
            Sleep.sleep(300, 600); // Notice mistake
            Mouse.move(target); // Correct
        }

        // Perform click
        if (rightClick) {
            Mouse.click(target, true);
        } else {
            Mouse.click(target);
        }

        return true;
    }

    /**
     * Generate Bezier curve path from start to end
     */
    private Point[] generateBezierPath(Point start, Point end) {
        int distance = (int) start.distance(end);
        int steps = Math.max(10, Math.min(50, distance / 10)); // 10-50 steps

        // Control points for Bezier curve
        Point control1 = generateControlPoint(start, end, 0.33);
        Point control2 = generateControlPoint(start, end, 0.66);

        Point[] path = new Point[steps];

        for (int i = 0; i < steps; i++) {
            double t = (double) i / (steps - 1);
            path[i] = calculateBezierPoint(start, control1, control2, end, t);
        }

        return path;
    }

    /**
     * Generate control point for Bezier curve with randomness
     */
    private Point generateControlPoint(Point start, Point end, double ratio) {
        int midX = (int) (start.x + (end.x - start.x) * ratio);
        int midY = (int) (start.y + (end.y - start.y) * ratio);

        // Add perpendicular offset for curve
        int distance = (int) start.distance(end);
        int maxOffset = Math.min(100, distance / 4);
        int offset = random.nextInt(maxOffset * 2) - maxOffset;

        // Calculate perpendicular direction
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length > 0) {
            double perpX = -dy / length;
            double perpY = dx / length;

            midX += (int) (perpX * offset);
            midY += (int) (perpY * offset);
        }

        return new Point(midX, midY);
    }

    /**
     * Calculate point on cubic Bezier curve
     */
    private Point calculateBezierPoint(Point p0, Point p1, Point p2, Point p3, double t) {
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double uuu = uu * u;
        double ttt = tt * t;

        int x = (int) (uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x);
        int y = (int) (uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y);

        return new Point(x, y);
    }

    /**
     * Follow path with speed variation
     */
    private void followPath(Point[] path, Point target) {
        for (int i = 0; i < path.length; i++) {
            Mouse.move(path[i]);

            // Speed varies: fast at start, slow near end
            double progress = (double) i / path.length;
            double speed = 1.0 - (progress * 0.7); // 100% -> 30% speed

            int delay = (int) (5 + (15 * (1 - speed)) * speedMultiplier);
            delay += random.nextGaussian() * 3; // Add jitter

            if (delay > 0) {
                Sleep.sleep(Math.max(1, delay));
            }

            // Random pause (5% chance)
            if (random.nextInt(100) < 5) {
                Sleep.sleep(50, 150);
            }
        }
    }

    /**
     * Check if should overshoot target
     */
    private boolean shouldOvershoot() {
        return random.nextInt(100) < 20; // 20% chance
    }

    /**
     * Overshoot target slightly and correct
     */
    private void performOvershoot(Point target) {
        Point current = Mouse.getPosition();

        // Overshoot by 10-30 pixels past target
        int overshoot = 10 + random.nextInt(20);
        double angle = Math.atan2(target.y - current.y, target.x - current.x);

        Point overshootPoint = new Point(
                target.x + (int) (Math.cos(angle) * overshoot),
                target.y + (int) (Math.sin(angle) * overshoot)
        );

        Mouse.move(overshootPoint);
        Sleep.sleep(50, 150); // Notice overshoot
        Mouse.move(target); // Correct back
    }

    /**
     * Perform micro-correction to precisely hit target
     */
    private void performMicroCorrection(Point target) {
        Point current = Mouse.getPosition();
        int distance = (int) current.distance(target);

        if (distance > 2 && distance < 15) {
            // Small adjustment
            Sleep.sleep(20, 50);
            Mouse.move(target);
        }
    }

    /**
     * Move mouse naturally off-screen or to random position
     */
    public void moveOffScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        // Pick random edge
        Point target;
        switch (random.nextInt(4)) {
            case 0: // Top
                target = new Point(random.nextInt(screen.width), 0);
                break;
            case 1: // Right
                target = new Point(screen.width - 1, random.nextInt(screen.height));
                break;
            case 2: // Bottom
                target = new Point(random.nextInt(screen.width), screen.height - 1);
                break;
            default: // Left
                target = new Point(0, random.nextInt(screen.height));
                break;
        }

        move(target);
    }

    /**
     * Set mouse speed (affects all movements)
     * @param speed Speed multiplier (0.5 = slow, 2.0 = fast)
     */
    public void setSpeed(double speed) {
        try {
            MouseSettings.setSpeed((int) (speed * 100));
        } catch (Exception e) {
            Logs.debug("Could not set mouse speed: " + e.getMessage());
        }
    }
}