package com.mineui.ui.tree;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import com.mineui.ui.spec.UiSpecParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlexLayoutTest {

    /** 假字体：每字符 6px，行高 9px。 */
    private static final TextMeasurer TEXT = new TextMeasurer() {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    private static MeasureContext ctx(float width, float height) {
        return new MeasureContext(width, height, width, height, TEXT, StateAccess.EMPTY);
    }

    private static NodeStyle style(SizeSpec width, SizeSpec height, float gap, float grow) {
        return new NodeStyle(null, width, height, Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, gap,
                0f, null, 0f, null, 0f, 0f, null, 0, false, false,
                BooleanSpec.TRUE, null, false, null, null, null, null, null, 1f, null,
                0f, BooleanSpec.TRUE, grow);
    }

    private static BoxNode box(SizeSpec width, SizeSpec height, float grow) {
        return new BoxNode(style(width, height, 0f, grow));
    }

    @Test
    void rowGrowFillsRemainingWidthAfterGap() {
        RowNode row = new RowNode(style(SizeSpec.px(200), SizeSpec.auto(), 10f, 0f));
        BoxNode left = box(SizeSpec.px(30), SizeSpec.px(10), 0f);
        BoxNode right = box(SizeSpec.auto(), SizeSpec.px(10), 1f);
        row.addChild(left);
        row.addChild(right);

        row.measure(ctx(200, 200));

        assertEquals(30f, left.width(), 0.001f);
        assertEquals(160f, right.width(), 0.001f); // 200 - 30 - gap 10
        assertEquals(200f, row.width(), 0.001f);

        row.layout(0, 0);
        assertEquals(40f, right.x(), 0.001f);
        assertEquals(200f, right.x() + right.width(), 0.001f);
    }

    @Test
    void percentageLeftColumnPlusGrowRightColumnFillsRow() throws Exception {
        RowNode row = new RowNode(style(SizeSpec.px(200), SizeSpec.auto(), 0f, 0f));
        BoxNode left = box(SizeSpec.parse(new JsonPrimitive("20%")), SizeSpec.px(10), 0f);
        BoxNode right = box(SizeSpec.auto(), SizeSpec.px(10), 1f);
        row.addChild(left);
        row.addChild(right);

        row.measure(ctx(200, 200));

        assertEquals(40f, left.width(), 0.001f);
        assertEquals(160f, right.width(), 0.001f);
        row.layout(0, 0);
        assertEquals(40f, right.x(), 0.001f);
        assertEquals(200f, right.x() + right.width(), 0.001f);
    }

    @Test
    void columnGrowFillsMiddleAndPinsComposerToBottom() {
        ColumnNode column = new ColumnNode(style(SizeSpec.px(200), SizeSpec.px(200), 5f, 0f));
        BoxNode header = box(SizeSpec.px(200), SizeSpec.px(20), 0f);
        BoxNode middle = box(SizeSpec.px(200), SizeSpec.auto(), 1f);
        BoxNode composer = box(SizeSpec.px(200), SizeSpec.px(30), 0f);
        column.addChild(header);
        column.addChild(middle);
        column.addChild(composer);

        column.measure(ctx(200, 200));

        // 200 - header 20 - composer 30 - gap 5×2 = 140
        assertEquals(140f, middle.height(), 0.001f);
        assertEquals(200f, column.height(), 0.001f);

        column.layout(0, 0);
        assertEquals(0f, header.y(), 0.001f);
        assertEquals(25f, middle.y(), 0.001f);
        assertEquals(170f, composer.y(), 0.001f); // 底边固定：200 - 30
        assertEquals(200f, composer.y() + composer.height(), 0.001f);
    }

    @Test
    void parserAppliesGrowFromJson() throws Exception {
        RowNode row = (RowNode) UiSpecParser.parse(JsonParser.parseString("""
                {"type":"row","width":200,"gap":10,"children":[
                  {"type":"box","width":30,"height":10},
                  {"type":"box","height":10,"grow":1}
                ]}
                """).getAsJsonObject());

        assertEquals(1f, row.children().get(1).style().grow(), 0.001f);

        row.measure(ctx(200, 200));

        assertEquals(30f, row.children().get(0).width(), 0.001f);
        assertEquals(160f, row.children().get(1).width(), 0.001f);
    }
}
