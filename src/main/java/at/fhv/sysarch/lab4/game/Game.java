package at.fhv.sysarch.lab4.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import at.fhv.sysarch.lab4.physics.BallPocketedListener;
import at.fhv.sysarch.lab4.physics.BallsCollisionListener;
import at.fhv.sysarch.lab4.physics.BallsRestListener;
import at.fhv.sysarch.lab4.physics.Physics;
import at.fhv.sysarch.lab4.rendering.Renderer;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.RaycastResult;
import org.dyn4j.geometry.Ray;
import org.dyn4j.geometry.Vector2;

public class Game implements BallPocketedListener, BallsRestListener, BallsCollisionListener {
    private final Renderer renderer;
    private final Physics physics;

    private double cueStartX;
    private double cueStartY;

    private int[] playerScores = {0,0,0};

    private int currentTurn;
    private boolean turnIsActive;

    private boolean foulOccured;
    private boolean whitePocketed;
    private boolean ballsTouched;
    private boolean anyBallsPocketed;

    public Game(Renderer renderer, Physics physics) {
        this.renderer = renderer;
        this.physics = physics;

        currentTurn = 1;
        turnIsActive = false;
        foulOccured = false;
        whitePocketed = false;
        ballsTouched = false;
        anyBallsPocketed = false;

        renderer.setActionMessage("Current Player: Player 1");
        renderer.setStrikeMessage("Waiting for strike...");

        physics.setPocketedListener(this);
        physics.setBallsRestListener(this);
        physics.setBallsCollisionListener(this);
        this.initWorld();
    }

    public void onMousePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        cueStartX = this.renderer.screenToPhysicsX(x);
        cueStartY = this.renderer.screenToPhysicsY(y);

        this.renderer.setCueStart(x, y);
    }

    public void onMouseReleased(MouseEvent e) {
        this.renderer.releaseCue();

        if (!turnIsActive) {

            double x = e.getX();
            double y = e.getY();
            double cueEndX = this.renderer.screenToPhysicsX(x);
            double cueEndY = this.renderer.screenToPhysicsY(y);

            List<RaycastResult> results = physics.strike(cueStartX, cueStartY, cueEndX, cueEndY);
            var hitBall = results.stream().filter(o -> o.getBody().getUserData() instanceof Ball).findFirst();
            if(hitBall.isPresent()) {

                turnIsActive = true;
                renderer.setStrikeMessage("Balls are moving...");
                renderer.setCueColor(Color.RED);
                var ball = hitBall.get().getBody();
                Ball checkWhite = (Ball) ball.getUserData();
                if (!checkWhite.isWhite()) {
                    foul("Only hit the white ball");
                }
                physics.hitBall(ball, cueStartX, cueStartY, cueEndX, cueEndY);
            }

        }
    }

    public void setOnMouseDragged(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        this.renderer.setCueEnd(x, y);
    }

    private void placeBalls(List<Ball> balls) {
        Collections.shuffle(balls);

        // positioning the billard balls IN WORLD COORDINATES: meters
        int row = 0;
        int col = 0;
        int colSize = 5;

        double y0 = -2*Ball.Constants.RADIUS*2;
        double x0 = -Table.Constants.WIDTH * 0.25 - Ball.Constants.RADIUS;

        for (Ball b : balls) {
            double y = y0 + (2 * Ball.Constants.RADIUS * row) + (col * Ball.Constants.RADIUS);
            double x = x0 + (2 * Ball.Constants.RADIUS * col);

            b.setPosition(x, y);
            b.getBody().setLinearVelocity(0, 0);
            renderer.addBall(b);

            row++;

            if (row == colSize) {
                row = 0;
                col++;
                colSize--;
            }
        }
    }

    private void initWorld() {
        generateBalls();

        Ball.WHITE.setPosition(Table.Constants.WIDTH * 0.25, 0);
        physics.getWorld().addBody(Ball.WHITE.getBody());
        renderer.addBall(Ball.WHITE);
        
        Table table = new Table();
        physics.getWorld().addBody(table.getBody());
        renderer.setTable(table);
    }

    @Override
    public boolean onBallPocketed(Ball b) {
        if (b.isWhite()) {
            //foul
            foul("White ball pocketed!");
            foulOccured = true;

        } else if (!foulOccured){
            // get point
            anyBallsPocketed = true;
            playerScores[currentTurn]++;
            updateScore();
        }
        physics.getWorld().removeBody(b.getBody());
        renderer.removeBall(b);
        return true;
    }

    private void generateBalls() {
        List<Ball> balls = new ArrayList<>();

        for (Ball b : Ball.values()) {
            if (b == Ball.WHITE)
                continue;
            physics.getWorld().addBody(b.getBody());
            balls.add(b);
        }
        this.placeBalls(balls);
    }

    @Override
    public boolean allBallsPocketed() {
        generateBalls();
        whiteToOrigin();
        return true;
    }

    private void whiteToOrigin() {

        Ball.WHITE.setPosition(Table.Constants.WIDTH * 0.25, 0);
        Body white = Ball.WHITE.getBody();
        white.setLinearVelocity(new Vector2(0,0));
        if (physics.getWorld().containsBody(Ball.WHITE.getBody())) {
            physics.getWorld().removeBody(Ball.WHITE.getBody());
            renderer.removeBall(Ball.WHITE);
        }
        physics.getWorld().addBody(white);
        renderer.addBall(Ball.WHITE);
    }

    @Override
    public void objectsAreResting() {
        if (turnIsActive) {
            // turn is over
            turnIsActive = false;
            renderer.setCueColor(Color.BLACK);

            // a foul occured
            renderer.setFoulMessage("");
            if(!ballsTouched) foul("Balls didn't touch");
            if(foulOccured) whiteToOrigin();
            foulOccured = false;

            // next player has the turn
            if (currentTurn == 1 && anyBallsPocketed) {
                anyBallsPocketed = false;
            } else if (currentTurn == 2 && anyBallsPocketed) {
                anyBallsPocketed = false;
            } else {
                currentTurn = currentTurn == 1 ? 2 : 1;
            }

            // change messages
            renderer.setActionMessage("Current Player: Player " + currentTurn);
            renderer.setStrikeMessage("Waiting for strike...");
        }
    }

    private void foul(String message) {
        renderer.setFoulMessage(message);
        if (!foulOccured) playerScores[currentTurn]--;
        updateScore();
        foulOccured = true;
    }

    private void updateScore() {
        renderer.setPlayer1Score(playerScores[1]);
        renderer.setPlayer2Score(playerScores[2]);
    }

    @Override
    public void ballsTouched(Ball b1, Ball b2) {
        if (b1.isWhite() || b2.isWhite()) {
            ballsTouched = true;
            renderer.setFoulMessage("");
        }
    }
}