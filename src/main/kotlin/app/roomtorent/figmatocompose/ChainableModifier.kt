package app.roomtorent.figmatocompose

import BaseNodeMixin
import BlendMixin
import ColorStop
import CornerMixin
import GeometryMixin
import GradientPaint
import LayoutMixin
import RectangleCornerMixin
import ShadowEffect
import SolidPaint

class ModifierChain(modifiersFromParent: (ModifierChain.() -> Unit)? = null) {
    val ownModifiers = arrayListOf<ChainableModifier>()
    val inheritedModifiers: ArrayList<ChainableModifier>

    fun getBuiltOptimized(): String {
        var combined =
            ownModifiers.apply {
                if (this.isNotEmpty() && inheritedModifiers.isNotEmpty()) add(CustomModSeparator())
            } + inheritedModifiers
        if (combined.filterNot { it is CustomModSeparator || it is None }.isEmpty()) return "Modifier"
        if (Settings.Optimizations.omitExtraShadows) {
            val biggestShadow: Shadow? =
                combined
                    .filterIsInstance<Shadow>()
                    .maxByOrNull { it.dp }
            if (biggestShadow != null) {
                val withOnlyBiggestShadow: List<ChainableModifier> =
                    combined
                        .filter { it !is Shadow }
                        .apply { (this as ArrayList<ChainableModifier>).add(biggestShadow) }
                combined = withOnlyBiggestShadow
            }
        }
        // Make sure that given equal order, clips come first
        val (clips, notClips) = combined.partition { it is CornerRadius || it is RectangleCornerRadius }
        val clipsFirst = clips + notClips
        val clipsFirstSorted = clipsFirst.sortedBy { it.order }
        return clipsFirstSorted.fold("Modifier") { acc, chainableModifier: ChainableModifier ->
            chainableModifier.addToChain(
                acc,
            )
        }
    }

//    fun getBuiltRaw(): String = inheritedModifiers.fold(initial = "modifier = Modifier") { acc, chainableModifier -> chainableModifier.addToChain(acc) }

    // Execute modifiers from parent to add them as operations
    init {
        // The type safer builders add modifiers to ownModifiers, so we construct a virtual parent modifier,
        // run the builder again and set our inheritedModifiers to the virtual parent's ownModifiers
        inheritedModifiers =
            when {
                modifiersFromParent != null -> {
                    val virtualParentModifier = ModifierChain()
                    modifiersFromParent.invoke(virtualParentModifier)
                    virtualParentModifier.ownModifiers
                }
                else -> arrayListOf()
            }
    }

    abstract class ChainableModifier {
        open var order: Int = 0

        abstract fun addToChain(acc: String): String
    }

    class Tag(var tag: String) : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.tag(\"$tag\")"
    }

    class FillMaxSize() : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.fillMaxSize()"
    }

    class FillMaxWidth() : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.fillMaxWidth()"
    }

    class DrawOpacity(var amount: Float) : ChainableModifier() {
        override fun addToChain(acc: String) = acc + ".drawOpacity(${amount}f)"
    }

    class Size(val widthDp: Number, val heightDp: Number) : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.size($widthDp.dp, $heightDp.dp)"
    }

    class Width(var widthDp: Number) : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.width($widthDp.dp)"
    }

    class Height(var heightDp: Number) : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.height($heightDp.dp)"
    }

    class FillMaxHeight() : ChainableModifier() {
        override fun addToChain(acc: String) = "$acc.fillMaxHeight()"
    }

    class PaintVectorPaint(var drawableIntPath: String) : ChainableModifier() {
        override fun addToChain(acc: String) = acc + ".paint".args("VectorPainter".args("vectorResource".args(drawableIntPath)))
    }

    class CustomModSeparator() : ChainableModifier() {
        override fun addToChain(acc: String): String {
//            return if(acc == "Modifier") "+ Modifier"
            return acc
        }
    }

    class CornerRadius(val radius: Float) : ChainableModifier() {
        // Before rectanglecorneradius, to override this,
        override var order = -1100

        override fun addToChain(acc: String): String = "$acc.clip".args("RoundedCornerShape".args("$radius.dp"))
    }

    fun cornerRadius(radius: Float) = if (radius != 0f) ownModifiers.add(CornerRadius(radius)) else false

    class RectangleCornerRadius(
        val topLeftRadius: Double,
        val topRightRadius: Double,
        val bottomRightRadius: Double,
        val bottomLeftRadius: Double,
    ) : ChainableModifier() {
        // Should be before shadow
        override var order = -1000

        override fun addToChain(acc: String): String =
            "$acc.clip".args(
                "RoundedCornerShape".args("$topLeftRadius.dp, $topRightRadius.dp, $bottomRightRadius.dp, $bottomLeftRadius.dp"),
            )
    }

    fun rectangleCornerRadius(
        topLeftRadius: Double,
        topRightRadius: Double,
        bottomLeftRadius: Double,
        bottomRightRadius: Double,
        simplifyToCornerRadiusIfEqual: Boolean = true,
        omitIfAllZero: Boolean = true,
    ) {
        if (topLeftRadius == topRightRadius && topRightRadius == bottomRightRadius && bottomRightRadius == bottomLeftRadius) {
            if (omitIfAllZero && topLeftRadius == 0.0) return
            if (simplifyToCornerRadiusIfEqual) {
                ownModifiers.add(CornerRadius(topLeftRadius.toFloat()))
                return
            }
        }
        ownModifiers.add(
            RectangleCornerRadius(
                topLeftRadius,
                topRightRadius,
                bottomLeftRadius,
                bottomRightRadius,
            ),
        )
    }

    class LinearGradientBackground(
        var stops: Array<ColorStop>,
        var width: Float,
        var gradientTransform: ArrayList<ArrayList<Double>>? = null,
    ) : ChainableModifier() {
        override fun addToChain(acc: String) =
            acc +
                ".background".args(
                    "Brush.horizontalGradient".args(
                        stops.joinToString { "${it.position}f to ${it.color?.toComposeColor()}" },
                        "startX = 0f",
                        "endX = $width.dp.value.toFloat()",
                    ),
                )
    }

    class Border() : ChainableModifier() {
        override fun addToChain(acc: String): String = "$acc."
    }

    class None() : ChainableModifier() {
        override fun addToChain(acc: String) = acc
    }

    class ConstrainAs(val tagName: String, val composeConstraintCode: String) : ChainableModifier() {
        override fun addToChain(acc: String): String {
            return "$acc.constrainAs".args(tagName).body(composeConstraintCode)
        }
    }

    /**
     * Adds new Compose constraints that specify the size and location within parent to resize as it does in the design
     *
     * Recommended to removeSizeModifiers, as the existing size modifiers will keep this composable at its literal dimensions
     * in the design
     */
    fun constrainAs(
        tagName: String,
        composeConstraintCode: String,
        removeSizeModifiers: Boolean = true,
    ) {
        ownModifiers.add(ConstrainAs(tagName, composeConstraintCode))
        if (removeSizeModifiers) ownModifiers.removeIf { it is Width || it is Height || it is Size }
    }

    class Shadow(var dp: Float, val modifierChain: ModifierChain) : ChainableModifier() {
        // Should be before background, but after clip/rounded corner
        override var order = -10000

        /**
         * Special consideration: If there is a Clip in this chain, then use it(first clip found)'s
         * Shape as well
         */
        override fun addToChain(acc: String): String {
            val combinedModifiers = (modifierChain.ownModifiers + modifierChain.inheritedModifiers)
            val firstCornerRadius: CornerRadius? = combinedModifiers.firstOrNull { it is CornerRadius } as CornerRadius?
            val firstRectangleCornerRadius: RectangleCornerRadius? =
                combinedModifiers.firstOrNull {
                    it is RectangleCornerRadius
                } as RectangleCornerRadius?
            val shadowShape =
                when {
                    firstRectangleCornerRadius != null ->
                        with(firstRectangleCornerRadius) {
                            "shape = ${"RoundedCornerShape".args(
                                "$topLeftRadius.dp, $topRightRadius.dp, $bottomRightRadius.dp, $bottomLeftRadius.dp",
                            )}"
                        }
                    firstCornerRadius != null -> "shape = ${"RoundedCornerShape".args(firstCornerRadius.radius.toDouble().roundedDp())}"
                    else -> ""
                }
            if (Settings.Optimizations.avoidAndroidShadowOptimization) {
                return "$acc.shadow".args("$dp.dp", "opacity = 0.99f", shadowShape)
            }
            return "$acc.shadow".args("$dp.dp", shadowShape)
        }
    }

    class Gravity(var alignment: AlignmentOption) : ChainableModifier() {
        override fun addToChain(acc: String) = acc + ".align(Alignment.${alignment.name})"
    }

    class Background(var color: RGBA, var opacityOverride: Float? = null) : ChainableModifier() {
        override fun addToChain(acc: String) = acc + ".background(${color.toComposeColor(opacityOverride)})"
    }

    class WrapContentSize() : ChainableModifier() {
        override fun addToChain(acc: String): String = "$acc.wrapContentSize".args()
    }

    enum class Alignment {
        CenterVertically,
        Top,
        Bottom,
    }

    class WrapContentHeight(val alignment: Alignment) : ChainableModifier() {
        override fun addToChain(acc: String): String = "$acc.wrapContentHeight".args("Alignment.${alignment.name}")
    }

    fun wrapContentHeight(alignment: Alignment) = ownModifiers.add(WrapContentHeight(alignment))

    /**
     * For setting the modifier of the first composable inside one of our own composables to the modifier passed as a parameter called "modifier"
     */
    class ClassProperties() : ChainableModifier() {
        override var order: Int = -100000

        override fun addToChain(acc: String): String = "modifier"
    }

    fun tag(tag: String) = ownModifiers.add(Tag(tag))

    fun fillMaxSize() = ownModifiers.add(FillMaxSize())

    fun fillMaxWidth() = ownModifiers.add(FillMaxWidth())

    fun drawOpacity(amount: Float) = ownModifiers.add(DrawOpacity(amount))

    fun size(
        widthDp: Number,
        heightDp: Number,
    ) = ownModifiers.add(Size(widthDp, heightDp))

    fun preferredWidth(widthDp: Number) = ownModifiers.add(Width(widthDp))

    fun preferredHeight(heightDp: Number) = ownModifiers.add(Height(heightDp))

    fun fillMaxHeight() = ownModifiers.add(FillMaxHeight())

    fun paintVectorPaint(drawableIntPath: String) = ownModifiers.add(PaintVectorPaint(drawableIntPath))

    fun linearGradientBackground(
        stops: Array<ColorStop>,
        width: Float,
        gradientTransform: ArrayList<ArrayList<Double>>? = null,
    ) = ownModifiers.add(LinearGradientBackground(stops, width, gradientTransform))

    fun none() = ownModifiers.add(None())

    fun shadow(dp: Float) = ownModifiers.add(Shadow(dp, this))

    fun gravity(alignment: AlignmentOption) = ownModifiers.add(Gravity(alignment))

    fun referredSize(
        widthDp: Number,
        heightDp: Number,
    ) = Size(widthDp, heightDp)

    fun background(
        color: RGBA,
        opacityOverride: Float? = null,
    ) = ownModifiers.add(Background(color, opacityOverride))

    fun customModSeparator() = ownModifiers.add(CustomModSeparator())

    fun addPassedProperties() = ownModifiers.add(ClassProperties())

    fun addStyleMods(node: BaseNodeMixin) {
        if (node is GeometryMixin) {
            node.fills
                ?.filter { it.visible }
                ?.forEach { fill ->
                    when {
                        // Different gradient types share the same Kotlin type, so for now look at fill.type
                        fill.type == "GRADIENT_LINEAR" ->
                            with(fill as GradientPaint) {
                                linearGradientBackground(
                                    this.gradientStops ?: arrayOf(),
                                    (node as LayoutMixin).width.toFloat(),
                                )
                            }
                        fill is SolidPaint -> background(fill.color, fill.opacity.toFloat())
                    }
                }
        }
        if (node is BlendMixin) {
            node.effects?.forEach { effect ->
                when (effect) {
                    is ShadowEffect ->
                        shadow(
                            effect.radius?.toFloat()
                                ?: throw java.lang.Exception("Has shadow effect but shadow effect has null radius"),
                        )
                }
            }
        }
        if (node is RectangleCornerMixin) {
            rectangleCornerRadius(
                node.topLeftRadius,
                node.topRightRadius,
                node.bottomLeftRadius,
                node.bottomRightRadius,
            )
        } else if (node is CornerMixin) {
            cornerRadius(node.cornerRadius.toFloat())
        }
    }

    enum class AlignmentOption() {
        Start,
        End,
        CenterHorizontally,
        CenterVertically,
    }
}
