package microtrafficsim.core.vis.tilebased;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import microtrafficsim.core.map.tiles.TileId;
import microtrafficsim.core.vis.Overlay;
import microtrafficsim.core.vis.UnsupportedFeatureException;
import microtrafficsim.core.vis.Visualizer;
import microtrafficsim.core.vis.VisualizerConfig;
import microtrafficsim.core.vis.context.RenderContext;
import microtrafficsim.core.vis.context.UniformManager;
import microtrafficsim.core.vis.context.VertexAttributeManager;
import microtrafficsim.core.vis.map.tiles.TileManager;
import microtrafficsim.core.vis.map.tiles.TileProvider;
import microtrafficsim.core.vis.opengl.DataTypes;
import microtrafficsim.core.vis.opengl.shader.attributes.VertexAttributes;
import microtrafficsim.core.vis.opengl.shader.uniforms.UniformMat4f;
import microtrafficsim.core.vis.opengl.utils.Color;
import microtrafficsim.core.vis.opengl.utils.DebugUtils;
import microtrafficsim.core.vis.view.OrthographicView;
import microtrafficsim.core.vis.view.View;
import microtrafficsim.math.Rect2d;
import microtrafficsim.math.Vec2d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static microtrafficsim.build.BuildSetup.DEBUG_VISUALIZATION;


public class TileBasedVisualizer implements Visualizer {

    private static final int DEFAULT_FPS = 0;
    private static final String GL_PROFILE = GLProfile.GL3;

    private RenderContext context;
    private OrthographicView view;

    private TileManager manager;
    private TreeMap<Integer, Overlay> overlays;

    // global uniforms
    private UniformMat4f uView;
    private UniformMat4f uProjection;

    private Color bgcolor = Color.fromRGB(0xFFFFFA);


    public TileBasedVisualizer(
            RenderContext context,
            OrthographicView view,
            TileProvider provider,
            ExecutorService worker)
    {
        this.context = context;
        this.view = view;

        this.overlays = new TreeMap<>();

        this.manager = new TileManager(provider, worker);
        provider.addTileChangeListener(new TileChangeListenerImpl());

        initShaderBindings();
    }

    private void initShaderBindings() {
        UniformManager uniforms = context.getUniformManager();
        VertexAttributeManager attributes = context.getVertexAttribManager();

        // initialize global uniform variables
        uView = (UniformMat4f) uniforms.putGlobalUniform("u_view", DataTypes.FLOAT_MAT4);
        uProjection = (UniformMat4f) uniforms.putGlobalUniform("u_projection", DataTypes.FLOAT_MAT4);
        uniforms.putGlobalUniform("u_viewscale", DataTypes.FLOAT);
        uniforms.putGlobalUniform("u_viewport", DataTypes.FLOAT_VEC4);

        // initialize default attribute bindings
        attributes.putDefaultAttributeBinding("a_position", VertexAttributes.POSITION3);
        attributes.putDefaultAttributeBinding("a_position2", VertexAttributes.POSITION2);

        attributes.putDefaultAttributeBinding("a_normal", VertexAttributes.NORMAL3);
        attributes.putDefaultAttributeBinding("a_normal2", VertexAttributes.NORMAL2);

        attributes.putDefaultAttributeBinding("a_texcoord3", VertexAttributes.TEXCOORD3);
        attributes.putDefaultAttributeBinding("a_texcoord", VertexAttributes.TEXCOORD2);

        attributes.putDefaultAttributeBinding("a_color", VertexAttributes.COLOR);
    }


    @Override
    public VisualizerConfig getDefaultConfig() throws UnsupportedFeatureException {
        GLProfile profile = GLProfile.get(GL_PROFILE);

        // check if required features are available
        ArrayList<String> missing = checkGLFeatureSet(profile);
        if (!missing.isEmpty())
            throw new UnsupportedFeatureException(missing);

        return new VisualizerConfig(profile, new GLCapabilities(profile), DEFAULT_FPS);
    }

    private ArrayList<String> checkGLFeatureSet(GLProfile profile) {
        ArrayList<String> missing = new ArrayList<>();

        if (!profile.isGL3()) {
            missing.add("GL_VERSION_3_X");
            return missing;
        }

		/*
		 * the above only checks for the major version, to check for the minor
		 * version we have to parse the availability String for GL3. Note that
		 * this is a check for the default device.
		 */
        String available = GLProfile.glAvailabilityToString();
        Pattern gl31 = Pattern.compile(".*GL3 true \\[(\\d)\\.(\\d).*");
        Matcher matcher = gl31.matcher(available);

        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));

            if (major < 3 || (major == 3 && minor < 2)) {
                missing.add("GL_VERSION_3_2");
                return missing;
            }
        } else {
            missing.add("GL_VERSION_3_2");
            return missing;
        }

        return missing;
    }


    @Override
    public void init(RenderContext context) {
        if (DEBUG_VISUALIZATION) {
            DebugUtils.setDebugGL(context.getDrawable());
            context.getDrawable().getGL().setSwapInterval(0);
        }

        // initialize OpenGL
        GL3 gl = context.getDrawable().getGL().getGL3();

        context.ClearColor.set(gl, bgcolor);
        context.ClearDepth.set(gl, 1.f);

        context.PrimitiveRestart.enable(gl);
        context.PrimitiveRestart.setIndex(gl, 0xFFFFFFFF);

        gl.glEnable(GL3.GL_CULL_FACE);

        resetView();

        manager.initialize(context);

        for (Overlay overlay: overlays.values())
            overlay.init(context, view);
    }

    @Override
    public void dispose(RenderContext context) {
        manager.dispose(context);

        for (Overlay overlay: overlays.values())
            overlay.dispose(context);
    }

    @Override
    public void display(RenderContext context) {
        uView.set(view.getViewMatrix());
        uProjection.set(view.getProjectionMatrix());

        GL3 gl = context.getDrawable().getGL().getGL3();
        gl.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT);
        gl.glViewport(0, 0, view.getSize().x, view.getSize().y);    // TODO: context state

        // update and draw
        manager.update(context, view);
        manager.display(context);

        for (Overlay overlay : overlays.values())
            overlay.display(context, view);
    }

    @Override
    public void reshape(RenderContext context, int x, int y, int width, int height) {
        view.resize(width, height);

        for (Overlay overlay : overlays.values())
            overlay.resize(context, view);
    }


    @Override
    public RenderContext getContext() {
        return context;
    }


    @Override
    public View getView() {
        return view;
    }

    @Override
    public void resetView() {
        Rect2d bounds = manager.getProjectedBounds();
        if (bounds == null) return;

        // center view
        Vec2d center = Vec2d.add(bounds.min(), bounds.max()).mul(0.5f);
        view.setPosition(center);

        // set zoom based on window width
        Vec2d dbounds = Vec2d.sub(bounds.max(), bounds.min());
        Vec2d dscreen = new Vec2d(view.getSize());

        double sx = (dscreen.x * 0.90f) / dbounds.x;
        double sy = (dscreen.y * 0.90f) / dbounds.y;

        view.setScale(Math.min(sx, sy));
    }


    @Override
    public Overlay putOverlay(int index, Overlay overlay) {
        return overlays.put(index, overlay);
    }

    @Override
    public Overlay removeOverlay(int index) {
        return overlays.remove(index);
    }

    @Override
    public Overlay getOverlay(int index) {
        return overlays.get(index);
    }

    @Override
    public Collection<Overlay> getAllOverlays() {
        return overlays.values();
    }


    private class TileChangeListenerImpl implements TileProvider.TileChangeListener {
        @Override
        public void tilesChanged() {
            resetView();
        }

        @Override public void tileChanged(TileId tile) {}
    }
}
