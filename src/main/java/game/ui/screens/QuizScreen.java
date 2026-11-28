package game.ui.screens;

import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.ui.BaseScreen;
import game.ui.GameFonts;
import game.ui.ImageLoader;
import game.ui.ScreenManager;
import game.ui.components.SketchAssets;
import game.ui.components.SketchButton;
import game.ui.components.SketchHearts;
import game.ui.components.SketchImage;
import game.ui.components.SketchLabel;
import game.ui.components.SketchLayout;
import game.ui.components.SketchLayout.Spec;
import game.ui.components.SketchRichText;
import game.ui.components.SketchScrollBarUI;
import game.ui.components.SketchText;
import game.ui.components.SpriteArt;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 퀴즈 전투 화면. 화면 표시와 사용자 입력만 맡는다.
 *
 * 정답 판정, HP 계산, 다음 문제 선택 같은 전투 규칙은 이 화면이 아니라 {@link QuizListener}를 구현한 쪽
 * (지금은 {@link QuizScreenDemo}, 나중에는 실제 게임 로직)이 맡는다.
 *
 * 흐름: showQuiz → (보기 클릭·단답 제출 또는 시간 초과) → 리스너가 판정 → showResult → '다음 문제' 클릭 → 리스너가 showQuiz
 * 또는 전투가 끝나면 showBattleEnd → '돌아가기'(데모: '다시 플레이') 클릭 → onReturnRequested.
 *
 * <h3>화면 구성 (손그림 시안, escape_quiz_assets의 개별 PNG + Swing 컴포넌트)</h3>
 * 1280×720(16:9) 기준 좌표를 {@link SketchLayout}이 창 크기에 맞춰 같은 배율로 배치하고 전체를 창 가운데에 둔다. 전체 시안을 배경 한 장으로 쓰지 않고
 * 로고·스테이지 틀·X 버튼·타이머 선과 불꽃·하트·캐릭터·문제 틀·보기 버튼·입력 틀·제출 버튼을 각각 그린다.
 * 객관식은 가로 4줄({@link ChoiceLayout#WIDE}, 기본) 또는 2×2({@link ChoiceLayout#GRID}), 단답형은 입력 틀 + 제출 버튼이다.
 * 보기 버튼을 누르면 곧바로 그 보기로 제출한다(숫자 키 1~9도 같다).
 */
public class QuizScreen extends BaseScreen {

    /** 화면에서 일어난 사용자 행동을 전투 로직에 알린다. 모든 호출은 EDT에서 일어난다. */
    public interface QuizListener {
        /** 답안 제출. 객관식은 보기 번호(1부터) 문자열, 단답형은 입력한 문자열이다. */
        void onAnswerSubmitted(Quiz quiz, String answer);

        /** 제한시간이 다 지났다. */
        void onTimeExpired(Quiz quiz);

        /** 결과 확인 후 '다음 문제' 버튼을 눌렀다. */
        void onNextRequested();

        /**
         * 전투가 끝난 뒤 복귀 버튼을 눌렀다. 실제 게임에서는 맵/스테이지 화면으로 돌아가고,
         * 독립 데모에서는 전투를 처음부터 다시 시작한다. 버튼은 한 번만 눌린다.
         */
        default void onReturnRequested() {
        }

        /**
         * 팝업(HP 경고, 일시정지, GAME OVER)에서 고른 선택. 팝업 하나당 한 번만 호출된다.
         * 화면은 타이머·입력만 처리하고, 선택에 따른 게임 진행(재시작, 화면 이동 등)은 받는 쪽이 정한다.
         */
        default void onPopupChoice(PopupChoice choice) {
        }
    }

    /** 한 문제의 결과 종류 */
    public enum ResultType {
        CORRECT("정답입니다!", "정답", new Color(30, 130, 70)),
        WRONG("오답입니다!", "오답", new Color(225, 0, 0)),
        TIMEOUT("시간 초과!", "시간 초과", new Color(235, 110, 0));

        private final String title;
        private final String shortTitle;
        private final Color color;

        ResultType(String title, String shortTitle, Color color) {
            this.title = title;
            this.shortTitle = shortTitle;
            this.color = color;
        }
    }

    /** 전투 화면 위에 뜨는 팝업 종류 */
    public enum PopupType {
        /** 플레이어 HP가 1이 되었을 때 경고 */
        LOW_HP,
        /** 일시정지 */
        PAUSE,
        /** HP 0 */
        GAME_OVER
    }

    /** 팝업에서 고를 수 있는 선택 */
    public enum PopupChoice {
        /** HP 경고: 계속 싸우기 */
        LOW_HP_CONTINUE(PopupType.LOW_HP, true),
        /** HP 경고: 회복 아이템 사용 (HP +1, 아이템 1개 소모는 받는 쪽이 처리하고 전투는 이어진다) */
        LOW_HP_USE_ITEM(PopupType.LOW_HP, true),
        /** HP 경고: 체력 보충하러 가기. 더 이상 팝업에 나오지 않으며 기존 코드 호환용으로만 남겼다. */
        LOW_HP_RECOVER(PopupType.LOW_HP, false),
        /** 일시정지: 계속하기 */
        PAUSE_RESUME(PopupType.PAUSE, true),
        /** 일시정지: 전투 중단 */
        PAUSE_ABORT(PopupType.PAUSE, false),
        /** GAME OVER: 미니게임 (미니게임 화면 연결은 받는 쪽이 한다) */
        GAME_OVER_MINIGAME(PopupType.GAME_OVER, false),
        /** GAME OVER: 현재 스테이지 재시작 */
        GAME_OVER_RESTART(PopupType.GAME_OVER, false),
        /** GAME OVER: 메인 화면으로 돌아가기 */
        GAME_OVER_MAIN(PopupType.GAME_OVER, false),
        /** GAME OVER: 스테이지 선택으로 돌아가기. 팝업에는 더 이상 나오지 않으며 기존 코드 호환용으로만 남겼다. */
        GAME_OVER_STAGE_SELECT(PopupType.GAME_OVER, false);

        private final PopupType popup;
        private final boolean continuesBattle;

        PopupChoice(PopupType popup, boolean continuesBattle) {
            this.popup = popup;
            this.continuesBattle = continuesBattle;
        }

        public PopupType popup() {
            return popup;
        }

        /** true면 전투를 이어 가고, false면 이 화면을 떠나는 선택이다 */
        public boolean continuesBattle() {
            return continuesBattle;
        }
    }

    /** 화면의 진행 단계 */
    public enum Phase {
        /** 아직 문제가 없다 */
        READY,
        /** 답을 입력받는 중 (타이머 동작) */
        ANSWERING,
        /** 답을 제출했거나 시간이 끝나 결과를 기다리는 중 (입력 잠금) */
        WAITING_RESULT,
        /** 결과와 해설을 보여 주는 중 */
        SHOWING_RESULT,
        /** 전투 종료. 더 이상 입력과 진행을 받지 않는다 */
        FINISHED
    }

    /**
     * 객관식 보기 배치. 기본은 문제마다 자동으로 고르는 {@link #AUTO}이고, WIDE/GRID로 지정하면 그 배치로 고정된다
     * (데모·테스트용, {@link #setChoiceLayout}). 어느 배치든 정답 처리는 같다.
     */
    public enum ChoiceLayout {
        /** 문제마다 자동 선택: 보기 네 개가 모두 짧으면 2×2, 하나라도 길면 가로 4줄 (기본) */
        AUTO,
        /** 보기 하나가 한 줄(button_wide.png), 세로로 쌓는다 */
        WIDE,
        /** 2행 2열(button_square.png) */
        GRID
    }

    /** 전투 대상 몬스터 종류 (이미지와 기본 이름) */
    public enum MonsterType {
        GOLEM("골렘", "golem.png", "골렘", "golem", "monster", "몬스터"),
        MIMIC("미믹", "mimic.png", "미믹", "상자", "mimic"),
        GHOST("유령", "ghost.png", "유령", "고스트", "ghost", "trap", "함정");

        private final String displayName;
        private final String imageFile;
        private final List<String> keywords;

        MonsterType(String displayName, String imageFile, String... keywords) {
            this.displayName = displayName;
            this.imageFile = imageFile;
            this.keywords = List.of(keywords);
        }

        public String displayName() {
            return displayName;
        }

        /** resources 기준 이미지 경로 */
        public String imageFile() {
            return SketchAssets.ROOT + imageFile;
        }

        /** 전투 대상 이름에서 종류를 찾는다 (예: "버그 골렘" → GOLEM). 모르면 빈 값. */
        public static Optional<MonsterType> fromName(String name) {
            if (name == null) {
                return Optional.empty();
            }
            String lower = name.toLowerCase(Locale.ROOT);
            // 구체적인 이름(골렘, 미믹, 유령 …)을 먼저 보고, 그것이 없을 때만 일반 이름(monster, 몬스터)을 본다
            for (MonsterType type : values()) {
                for (String keyword : type.keywords) {
                    if (!GENERIC_KEYWORDS.contains(keyword) && lower.contains(keyword)) {
                        return Optional.of(type);
                    }
                }
            }
            for (MonsterType type : values()) {
                for (String keyword : type.keywords) {
                    if (GENERIC_KEYWORDS.contains(keyword) && lower.contains(keyword)) {
                        return Optional.of(type);
                    }
                }
            }
            return Optional.empty();
        }

        /** 어느 종류인지 알려 주지 못하는 일반 이름: 다른 이름이 함께 있으면 그쪽을 우선한다 */
        private static final List<String> GENERIC_KEYWORDS = List.of("monster", "몬스터");
    }

    // ---------------------------------------------------------------------
    // 1280×720(16:9) 기준 좌표. 시안(quiz_design_v2)에서 잰 위치를 다듬은 값이며, 창 크기가 달라져도 SketchLayout이
    // 같은 배율로 옮기고 화면 전체를 창 가운데에 놓는다 (문제 틀은 항상 가로 정중앙).
    // 이미지 크기는 원본(투명 여백 제외)의 0.5배라서 틀·버튼·입력창의 손그림 선 굵기가 서로 같다.
    // ---------------------------------------------------------------------
    private static final Spec LOGO = SketchLayout.center(28, 26, 200, 52);
    private static final Spec STAGE_FRAME = SketchLayout.center(244, 26, 148, 52);
    private static final Spec STAGE_TEXT = SketchLayout.center(254, 31, 128, 42);
    private static final Spec EXIT_BUTTON = SketchLayout.center(1196, 22, 48, 48);
    private static final Spec ESC_TEXT = SketchLayout.center(1176, 70, 88, 38);
    private static final Spec TIME_TEXT = SketchLayout.center(440, 78, 400, 54);
    private static final double LINE_X = 345;
    private static final double LINE_W = 590;
    private static final Spec TIMER_LINE = SketchLayout.center(LINE_X, 138, LINE_W, 6);
    private static final double FIRE_W = 40;
    private static final double FIRE_H = 56;
    private static final double FIRE_Y = 108;

    /** 좌우 캐릭터 영역: 각각 280px 폭, 가운데 문제 틀과 같은 거리(바깥 여백 40px) */
    /** 위에서부터 HP 줄 → 이름 → 캐릭터 순서. 좌우 HP 줄·이름 줄의 높이는 같고, 이름은 각 캐릭터 칸의 가로 중앙에 놓인다. */
    private static final Spec PLAYER_HP = SketchLayout.center(40, 186, 280, 44);
    private static final Spec PLAYER_NAME = SketchLayout.center(40, 234, 280, 46);
    /** 캐릭터 그림 칸은 좌우로 CHAR_PAD만큼 더 넓다: 공격 때 앞으로 나가거나 흔들려도 잘리지 않게 하는 여유 */
    private static final double CHAR_PAD = 24;
    /** 주인공·골렘·미믹의 발 밑 기준선 (유령은 이보다 위에 떠 있다) */
    private static final double BASE_Y = 660;
    private static final Spec PLAYER_IMAGE = SketchLayout.center(40 - CHAR_PAD, BASE_Y - 380, 280 + 2 * CHAR_PAD, 380);
    private static final Spec MONSTER_HP = SketchLayout.center(960, 186, 280, 44);
    private static final Spec MONSTER_NAME = SketchLayout.center(960, 234, 280, 46);
    /**
     * 몬스터 그림 칸(종류별, 투명 여백을 뺀 그림 기준). 골렘·미믹은 그림 위쪽이 y≈325~335, 아래쪽이 y≈590이고
     * 유령은 공중에 떠 있어 아래쪽이 더 높다(≈550). 주인공(발 y=660)과 바닥을 억지로 맞추지 않는다.
     */
    private static Spec monsterSpec(MonsterType type) {
        return switch (type) {
            case GHOST -> SketchLayout.center(970 - CHAR_PAD, 310, 260 + 2 * CHAR_PAD, 240);
            case MIMIC -> SketchLayout.center(960 - CHAR_PAD, 335, 280 + 2 * CHAR_PAD, 255);
            default -> SketchLayout.center(960 - CHAR_PAD, 325, 280 + 2 * CHAR_PAD, 265);
        };
    }
    /** 공격·피격 연출 */
    private static final double LUNGE_PX = 14;
    private static final double SHAKE_PX = 8;
    private static final int LUNGE_MS = 280;
    private static final int SHAKE_MS = 320;
    private static final int HEAL_MS = 420;
    private static final int FLOAT_MS = 650;
    private static final double FLOAT_FONT = 30;
    /** 숫자가 뜨기 시작하는 높이: 주인공은 머리 왼쪽 빈 곳, 몬스터는 머리 위 (이름·HP 줄과 얼굴을 가리지 않는다) */
    private static final double FLOAT_Y_PLAYER = 352;
    private static final double FLOAT_Y_MONSTER = 366;
    private static final double FLOAT_RISE = 44;
    private static final double PLAYER_CENTER_X = 100;
    private static final double MONSTER_CENTER_X = 1100;
    /** 제출 직후 정답·오답 색을 보여 주는 시간 (이 뒤에 결과 화면으로 넘어간다) */
    private static final int FEEDBACK_MS = 220;
    private static final Color FEEDBACK_OK = new Color(188, 226, 190);
    private static final Color FEEDBACK_BAD = new Color(244, 190, 190);
    private static final Color DAMAGE_RED = new Color(215, 30, 30);
    private static final Color HEAL_GREEN = new Color(30, 150, 70);
    private static final Color OK_BG = new Color(0xE7, 0xF1, 0xE6);
    private static final Color OK_TEXT = new Color(24, 96, 50);
    private static final Color BAD_BG = new Color(0xFA, 0xE6, 0xE6);
    private static final Color BAD_TEXT = new Color(160, 28, 28);
    private static final Color TRACK_GRAY = new Color(224, 222, 218);
    private static final Color TIMER_ORANGE = new Color(240, 140, 40);

    private static final Spec QUIZ_FRAME = SketchLayout.center(332, 172, 616, 516);
    /** 문제 틀 안쪽 안전 영역: 위 40px, 좌우 40px, 아래 30px. 모든 내용이 이 안에 들어간다. */
    private static final double CX = 372;
    private static final double CW = 536;
    private static final double CY0 = 212;
    private static final double CY1 = 658;
    private static final double CH = CY1 - CY0;

    private static final double WIDE_H = 48;
    private static final double WIDE_H_MAX = 60;
    private static final double WIDE_GAP = 14;
    private static final double GRID_W = 256;
    private static final double GRID_H = 65;
    private static final double GRID_COL_GAP = 24;
    private static final double GRID_ROW_GAP = 14;
    private static final double INPUT_H = 54;
    private static final double SUBMIT_H = 48;
    private static final double HINT_GAP = 6;
    private static final double HINT_H = 22;
    private static final double FONT_HINT = 16;
    /** 단답형 입력창이 비어 있을 때 흐리게 보이는 안내 */
    static final String ANSWER_HINT = "정답을 입력하세요.";
    private static final double INPUT_GAP = 12;
    /** 문제 글 시작 위치를 틀 모서리에서 띄우는 양 (1280×720 기준 px) */
    private static final double Q_OFFSET_X = 12;
    private static final double Q_OFFSET_Y = 10;
    /** 단답형은 남는 공간이 충분하면 입력창 묶음을 이만큼 더 아래로 내린다 */
    private static final double INPUT_EXTRA_DROP = 26;
    private static final double ACTION_W = 186;
    private static final double ACTION_H = 52;
    private static final double ACTION_Y = CY1 - ACTION_H;
    /** 문제 글과 보기(입력창) 사이 최소 간격 */
    private static final double MIN_GAP = 10;
    private static final double PREF_GAP = 22;
    private static final double GRID_H_MAX = 80;

    /** 보기 버튼 글자 칸의 안쪽 여백 비율 (가로로 긴 버튼 / 정사각형에 가까운 버튼) */
    private static final double WIDE_PAD_X = 0.045;
    private static final double WIDE_PAD_Y = 0.09;
    private static final double GRID_PAD_X = 0.11;
    private static final double GRID_PAD_Y = 0.12;

    private static final Spec ACTION_BUTTON = SketchLayout.center(640 - ACTION_W / 2, ACTION_Y, ACTION_W, ACTION_H);

    private static final Spec RESULT_TITLE = SketchLayout.center(CX, CY0, CW, 46);
    private static final Spec RESULT_DETAIL = SketchLayout.center(CX, CY0 + 50, CW, 30);
    private static final Spec DIVIDER = SketchLayout.center(CX, CY0 + 90, CW, 6);
    private static final double RESULT_BODY_Y = CY0 + 120;

    private static final Spec POPUP_TITLE = SketchLayout.center(CX, 234, CW, 58);
    private static final Spec POPUP_DIVIDER = SketchLayout.center(CX, 302, CW, 6);
    private static final Spec POPUP_MESSAGE_2 = SketchLayout.center(CX, 326, CW, 150);
    private static final Spec POPUP_MESSAGE_3 = SketchLayout.center(CX, 326, CW, 90);

    // 글자 크기 (1280×720 기준 px). 화면 배율을 곱해서 쓴다.
    private static final double FONT_STAGE = 32;
    private static final double FONT_ESC = 30;
    private static final double FONT_TIME = 41;
    private static final double FONT_NAME = 34;
    private static final double FONT_HP = 36;
    private static final double FONT_INPUT = 22;
    private static final double FONT_ACTION = 24;
    private static final double FONT_POPUP_TITLE = 40;
    private static final double FONT_POPUP_MESSAGE = 22;
    private static final double FONT_POPUP_BUTTON = 22;
    private static final double FONT_RESULT_TITLE = 36;
    private static final double FONT_RESULT_DETAIL = 22;
    private static final double FONT_BODY_MAX = 22;
    private static final double FONT_BODY_MIN = 20;
    private static final double FONT_CHOICE_MAX = 24;
    private static final double FONT_CHOICE_MIN = 20;
    /** 문제 글자(문장, 코드) 크기 단계: 큰 것부터 문제와 보기가 함께 들어가는 첫 단계를 쓴다 */
    /** {문장 px, 코드 px, 줄 간격 배수}: 문장 줄 간격은 글자 크기의 1.3~1.4배 */
    private static final double[][] QUESTION_TIERS = {{24, 20, 1.38}, {23, 19, 1.36}, {22, 19, 1.33}, {22, 18, 1.30},
            {21, 17, 1.30}};

    private static final Color PAPER = new Color(250, 250, 248);
    private static final Color INK = new Color(20, 20, 20);
    private static final Color CODE_INK = new Color(45, 43, 41);
    private static final Color HINT_GRAY = new Color(140, 138, 136);
    private static final Color WARNING = new Color(225, 0, 0);

    private static final String ASSET_ROOT = SketchAssets.ROOT;
    /** 기본으로 읽는 캐릭터 이미지(resources 기준). 파일이 없으면 임시 표시가 나온다. */
    /** 퀴즈 화면에서만 쓰는 채색본(원본 player.png의 선은 그대로, 색만 입힌 별도 파일). 원본 파일은 수정하지 않는다. */
    public static final String PLAYER_IMAGE_FILE = ASSET_ROOT + "player_color.png";
    public static final String MONSTER_IMAGE_FILE = MonsterType.GHOST.imageFile();

    private static final int TIMER_TICK_MS = 50;
    private static final int WARNING_SECONDS = 5;
    /** showQuiz의 제한시간으로 쓰면 타이머 없이 문제를 낸다 (시간 초과 규칙이 없는 게임 모드용). */
    public static final int NO_TIME_LIMIT = 0;
    private static final String DEFAULT_RETURN_TEXT = "돌아가기";

    /**
     * 캐릭터 그림을 그리는 방법. 기본은 resources의 PNG이며, 다른 그림을 쓰려면
     * {@link #setPlayerArt}, {@link #setMonsterArt}로 이 구현을 넘긴다.
     * (x, y, w, h)는 캐릭터에게 주어진 영역이다.
     */
    @FunctionalInterface
    public interface CharacterArt {
        void paint(Graphics2D g2, int x, int y, int w, int h);
    }

    /** 이미지가 없을 때 쓰는 임시 표시 (이미지 그리기의 대체 그림으로도 쓴다). */
    public static final CharacterArt PLACEHOLDER_PLAYER = (g2, x, y, w, h) -> paintPlaceholder(g2, x, y, w, h, "PLAYER");
    public static final CharacterArt PLACEHOLDER_MONSTER = (g2, x, y, w, h) -> paintPlaceholder(g2, x, y, w, h, "MONSTER");

    private QuizListener listener;
    private Phase phase = Phase.READY;
    private Quiz currentQuiz;

    // 타이머
    private final Timer countdown;
    private int timeLimitSeconds;
    private long deadlineNanos;
    private long pausedRemainingMillis = -1;
    private double timeFraction = 1.0;
    private ResultType lastResultType;

    // 연출: 제출 직후 정답·오답 색, 공격·피격 애니메이션, 피해량·회복량 숫자. 판정·HP·타이머에는 영향을 주지 않는다.
    private final Timer feedbackTimer;
    private final Timer fxTimer;
    private boolean effectsEnabled = true;
    private boolean feedbackActive;
    private final List<Runnable> deferred = new ArrayList<>();
    private final List<Anim> anims = new ArrayList<>();
    private final List<Floater> floaters = new ArrayList<>();
    private int lastPlayerHp = -1;
    private int lastMonsterHp = -1;
    private int pendingPlayerLoss;
    private int pendingMonsterLoss;
    private final boolean[] numberHeld = new boolean[5];
    private final long[] numberHeldAt = new long[5];

    private enum AnimType { LUNGE, SHAKE, HEAL }

    private record Anim(CharacterView view, AnimType type, double dir, long start, int durMs) {
    }

    private record Floater(String text, Color color, boolean onPlayer, long start) {
    }

    // 화면 바탕과 이미지
    private ImageLoader imageLoader = new ImageLoader();
    private final SketchAssets assets = new SketchAssets(imageLoader);
    private final SketchLayout layout = new SketchLayout();
    private final Stage stage = new Stage();
    private String backgroundFile;
    private double appliedScale = -1;

    // 상단
    private final SketchLabel stageLabel = new SketchLabel("STAGE -", INK);
    private final SketchLabel timerLabel = new SketchLabel(" ", INK);
    private final SketchLabel escLabel = new SketchLabel("ESC", INK);
    private final TimerBar timerLine = new TimerBar();
    private final FxLayer fxLayer = new FxLayer();
    private Spec popupDividerSpec = POPUP_DIVIDER;
    private final SketchImage fire = new SketchImage(assets, SketchAssets.FIRE, 0.5f, 1f);
    private final SketchButton pauseButton;

    // 좌우 전투 캐릭터
    private final SketchLabel playerName = new SketchLabel("사용자", INK);
    private final SketchLabel monsterName = new SketchLabel("몬스터", INK);
    private final SketchHearts playerHearts = new SketchHearts(assets, "HP");
    private final SketchHearts monsterHearts = new SketchHearts(assets, "HP");
    private final CharacterView playerView = new CharacterView(PLACEHOLDER_PLAYER);
    private final CharacterView monsterView = new CharacterView(PLACEHOLDER_MONSTER);
    private MonsterType monsterType = MonsterType.GHOST;
    private int stageNumberOverride;

    // 문제 글 영역
    private final SketchRichText questionText = new SketchRichText();
    private final JScrollPane textScroll = new JScrollPane(questionText,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private String questionPrompt = "";
    private String questionCode = "";
    private ResultBlock resultBlock;
    /** 이번 문제에서 사용자가 제출한 답(보기 번호 문자열 또는 입력한 글). 오답 결과에서 "내가 고른 답"으로 보여 준다. 제출 전·시간 초과면 null */
    private String submittedAnswer;
    private double resultBodyPx;
    private FitStatus resultFit = FitStatus.FITS;
    private final SketchLabel resultTitle = new SketchLabel(" ", INK);
    private final SketchLabel resultDetail = new SketchLabel(" ", INK);
    private final SketchImage divider = new SketchImage(assets, SketchAssets.TIMER_LINE, 0.5f, 0.5f);
    private String endNote;
    private Color endColor = INK;

    // 보기 / 입력
    private ChoiceLayout choiceLayout = ChoiceLayout.AUTO;
    private final List<SketchButton> choiceButtons = new ArrayList<>();
    private final AnswerField inputField = new AnswerField();
    private final SketchLabel inputHint = new SketchLabel("Enter 키로 제출할 수 있습니다.", HINT_GRAY);
    private final SketchImage inputFrame = new SketchImage(assets, SketchAssets.INPUT_FRAME, 0.5f, 0.5f);
    private final SketchButton inputSubmitButton;
    private final SketchButton nextButton;
    private final SketchButton returnButton;
    private boolean nextWanted;
    private boolean returnWanted;

    // 팝업 (문제 틀 안에서 문제 대신 보인다: 일시정지 중 문제를 볼 수 없게)
    private final SketchLabel popupTitle = new SketchLabel(" ", INK);
    private final SketchLabel popupMessage = new SketchLabel(" ", INK);
    private final SketchButton popupPrimary;
    private final SketchButton popupSecondary;
    private final SketchButton popupTertiary;
    private int popupButtonCount;
    private PopupType popup;
    private PopupChoice popupPrimaryChoice;
    private PopupChoice popupSecondaryChoice;
    private PopupChoice popupTertiaryChoice;

    /** 결과 표시 내용: 판정 종류와 본문 줄들 */
    private record ResultBlock(ResultType type, String detail, String answer, String userAnswer, String explanation) {
    }

    public QuizScreen(ScreenManager screenManager) {
        super(screenManager, new BorderLayout());
        panel.setBackground(PAPER);
        panel.add(stage, BorderLayout.CENTER);
        stage.setLayout(layout);
        layout.setOnLayout(this::applyScale);

        pauseButton = new SketchButton(assets, SketchAssets.BUTTON_EXIT, "", this::requestPause);
        inputSubmitButton = new SketchButton(assets, SketchAssets.BUTTON_SUBMIT, "제출", this::submitInput);
        nextButton = new SketchButton(assets, SketchAssets.BUTTON_SUBMIT, "다음 문제", this::requestNext);
        returnButton = new SketchButton(assets, SketchAssets.BUTTON_SUBMIT, DEFAULT_RETURN_TEXT, this::requestReturn);
        popupPrimary = new SketchButton(assets, SketchAssets.BUTTON_SQUARE, " ", () -> choose(popupPrimaryChoice));
        popupSecondary = new SketchButton(assets, SketchAssets.BUTTON_SQUARE, " ", () -> choose(popupSecondaryChoice));
        popupTertiary = new SketchButton(assets, SketchAssets.BUTTON_SQUARE, " ", () -> choose(popupTertiaryChoice));

        countdown = new Timer(TIMER_TICK_MS, e -> onTimerTick());
        feedbackTimer = new Timer(FEEDBACK_MS, e -> endFeedback());
        feedbackTimer.setRepeats(false);
        fxTimer = new Timer(16, e -> stepFx());

        buildStage();
        setupKeyBindings();
        resetQuestionView();
        useCharacterImages(imageLoader, PLAYER_IMAGE_FILE, MONSTER_IMAGE_FILE);
        playerHearts.setHp(0, 0);
        monsterHearts.setHp(0, 0);
        syncPhaseUi();
    }

    /** 화면 요소를 만들어 붙인다. 먼저 붙인 것이 위에 그려진다: 글자·버튼 → 틀·선·그림 순서. */
    private void buildStage() {
        // 가장 위: 피해량·회복량 숫자 (마우스 입력은 통과시킨다)
        add(fxLayer, "fxLayer", SketchLayout.center(0, 0, SketchLayout.DESIGN_W, SketchLayout.DESIGN_H));
        // 위쪽에 놓이는 것들(버튼·글자·입력)
        add(popupPrimary, "popupPrimary", SketchLayout.center(CX, CY1 - GRID_H, GRID_W, GRID_H));
        add(popupSecondary, "popupSecondary", SketchLayout.center(CX + GRID_W + GRID_COL_GAP, CY1 - GRID_H, GRID_W, GRID_H));
        add(popupTertiary, "popupTertiary", SketchLayout.center(CX, CY1 - WIDE_H, CW, WIDE_H));
        add(popupTitle, "popupTitle", POPUP_TITLE);
        add(popupMessage, "popupMessage", POPUP_MESSAGE_2);
        add(resultTitle, "resultTitle", RESULT_TITLE);
        add(resultDetail, "resultDetail", RESULT_DETAIL);
        add(nextButton, "nextButton", ACTION_BUTTON);
        add(returnButton, "returnButton", ACTION_BUTTON);
        add(inputSubmitButton, "inputSubmitButton", ACTION_BUTTON);
        add(inputHint, "inputHint", SketchLayout.center(CX, 640, CW, HINT_H));
        add(inputField, "inputField", SketchLayout.center(CX + 24, 600, CW - 48, INPUT_H - 8));

        textScroll.setName("bodyScroll");
        textScroll.setBorder(BorderFactory.createEmptyBorder());
        textScroll.setOpaque(false);
        textScroll.getViewport().setOpaque(false);
        textScroll.getVerticalScrollBar().setUI(new SketchScrollBarUI());
        textScroll.getVerticalScrollBar().setOpaque(false);
        textScroll.getVerticalScrollBar().setUnitIncrement(16);
        questionText.setName("questionArea");
        add(textScroll, "bodyScroll", SketchLayout.center(CX, CY0, CW, 200));

        add(pauseButton, "pauseButton", EXIT_BUTTON);
        add(stageLabel, "stageLabel", STAGE_TEXT);
        add(timerLabel, "timerLabel", TIME_TEXT);
        add(escLabel, "escLabel", ESC_TEXT);
        add(playerName, "playerName", PLAYER_NAME);
        add(monsterName, "monsterName", MONSTER_NAME);
        add(playerHearts, "playerHearts", PLAYER_HP);
        add(monsterHearts, "monsterHearts", MONSTER_HP);
        add(fire, "fire", SketchLayout.center(LINE_X + LINE_W - FIRE_W / 2, FIRE_Y, FIRE_W, FIRE_H));
        add(divider, "divider", DIVIDER);

        // 아래쪽에 놓이는 것들(틀·선·그림)
        add(inputFrame, "inputFrame", SketchLayout.center(CX, 540, CW, INPUT_H));
        add(new SketchImage(assets, SketchAssets.QUIZ_FRAME, 0.5f, 0.5f), "quizFrame", QUIZ_FRAME);
        add(timerLine, "timerLine", TIMER_LINE);
        add(new SketchImage(assets, SketchAssets.STAGE_FRAME, 0.5f, 0.5f), "stageFrame", STAGE_FRAME);
        add(new SketchImage(assets, SketchAssets.LOGO, 0f, 0.5f), "logo", LOGO);
        add(playerView, "playerView", PLAYER_IMAGE);
        add(monsterView, "monsterView", monsterSpec(monsterType));

        inputField.setName("inputField");
        inputField.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        inputField.setOpaque(false);
        inputField.setForeground(INK);
        inputField.setCaretColor(INK);
        inputField.addActionListener(e -> submitInput()); // Enter로 제출
        inputField.setFocusTraversalKeysEnabled(false);
    }

    private void add(Component c, String name, Spec spec) {
        c.setName(name);
        stage.add(c, spec);
    }

    // ---------------------------------------------------------------------
    // 전투 로직이 호출하는 API
    // ---------------------------------------------------------------------

    public void setQuizListener(QuizListener listener) {
        this.listener = listener;
    }

    /**
     * 좌우 캐릭터 이름 표시. 몬스터 이름에 골렘/미믹/유령(상자 몬스터는 미믹)이 들어 있으면 그 이미지로 바뀐다.
     * 이름에서 종류를 알 수 없으면 지금 이미지를 그대로 둔다({@link #setMonsterType}으로 직접 정할 수 있다).
     */
    public void setCombatantNames(String playerNameText, String monsterNameText) {
        playerName.setText(playerNameText);
        Optional<MonsterType> type = MonsterType.fromName(monsterNameText);
        // 게임 화면이 오브젝트 종류 이름(MONSTER, MIMIC, TRAP)을 그대로 넘기면 한글 이름(골렘, 미믹, 유령)으로 바꿔 보인다
        boolean objectTypeName = monsterNameText != null
                && List.of("MONSTER", "MIMIC", "TRAP").contains(monsterNameText.strip().toUpperCase(Locale.ROOT));
        monsterName.setText(objectTypeName && type.isPresent() ? type.get().displayName() : monsterNameText);
        type.ifPresent(this::setMonsterType);
    }

    /** 전투 대상 몬스터 종류를 정하고 그 이미지를 보여 준다. */
    public void setMonsterType(MonsterType type) {
        if (type == null) {
            return;
        }
        this.monsterType = type;
        layout.setSpec(monsterView, monsterSpec(type));
        stage.revalidate();
        setMonsterImage(type.imageFile());
    }

    /**
     * 화면 위쪽 STAGE에 쓸 스테이지 번호(실제 플레이 중인 스테이지). 0 이하이면 문제 데이터의 스테이지 번호를 쓴다.
     * 새 전투를 시작할 때 컨트롤러가 정한다.
     */
    public void setStageNumber(int stageNumber) {
        this.stageNumberOverride = Math.max(0, stageNumber);
        if (currentQuiz != null) {
            stageLabel.setText("STAGE " + (stageNumberOverride > 0 ? stageNumberOverride : currentQuiz.getStageNo()));
        }
    }

    public MonsterType getMonsterType() {
        return monsterType;
    }

    /** 객관식 보기 배치를 정한다 (기본 AUTO: 문제마다 자동). WIDE/GRID는 고정. 지금 떠 있는 문제에도 바로 적용된다. */
    public void setChoiceLayout(ChoiceLayout newLayout) {
        if (newLayout == null) {
            throw new IllegalArgumentException("layout is null");
        }
        this.choiceLayout = newLayout;
        applyChoiceLayout();
        applyTextRegion();
        refitQuestion();
        refreshLayout();
    }

    public ChoiceLayout getChoiceLayout() {
        return choiceLayout;
    }

    /** 플레이어 캐릭터 그림을 바꾼다 (null이면 임시 표시로 되돌린다). */
    public void setPlayerArt(CharacterArt art) {
        playerView.setArt(art != null ? art : PLACEHOLDER_PLAYER);
    }

    /** 몬스터 그림을 바꾼다 (null이면 임시 표시로 되돌린다). */
    public void setMonsterArt(CharacterArt art) {
        monsterView.setArt(art != null ? art : PLACEHOLDER_MONSTER);
    }

    /**
     * resources의 투명 PNG로 캐릭터를 그린다. 파일이 없거나 읽지 못하면 그 캐릭터만 임시 표시로 나온다.
     * 이 화면이 만들어질 때 {@link #PLAYER_IMAGE_FILE}, {@link #MONSTER_IMAGE_FILE}로 한 번 호출된다.
     *
     * @param loader      이미지 읽기·기억을 맡은 로더 (다른 화면과 같은 것을 넘기면 캐시를 같이 쓴다). null이면 기존 것을 유지
     * @param playerFile  플레이어 이미지 파일명. null이면 임시 표시
     * @param monsterFile 몬스터 이미지 파일명. null이면 임시 표시
     */
    public void useCharacterImages(ImageLoader loader, String playerFile, String monsterFile) {
        if (loader != null) {
            this.imageLoader = loader;
        }
        setPlayerImage(playerFile);
        setMonsterImage(monsterFile);
    }

    /** 플레이어 이미지만 바꾼다. null이면 임시 표시 */
    public void setPlayerImage(String fileName) {
        setPlayerArt(fileName == null ? null : new SpriteArt(imageLoader, fileName, PLACEHOLDER_PLAYER));
    }

    /** 몬스터 이미지만 바꾼다 (몬스터 종류마다 다른 파일을 쓸 때). null이면 임시 표시 */
    public void setMonsterImage(String fileName) {
        setMonsterArt(fileName == null ? null : new SpriteArt(imageLoader, fileName, PLACEHOLDER_MONSTER));
    }

    /** 배경 이미지를 바꾼다 (resources 기준 경로). 기본은 없음(종이색 배경). */
    public void setBackgroundImage(String fileName) {
        this.backgroundFile = fileName;
        stage.repaint();
    }

    /** HP 표시 갱신: 현재 체력만큼 빨간 하트, 잃은 만큼 빈 하트 */
    public void updateHp(int playerHp, int playerMaxHp, int monsterHp, int monsterMaxHp) {
        if (effectsEnabled) {
            // 실제로 HP가 달라진 만큼만 연출한다: 판정 중 감소 → 맞은 쪽 피해량, 풀이·결과 중 증가 → 회복량
            if (playerMaxHp > 0 && lastPlayerHp >= 0) {
                int delta = playerHp - lastPlayerHp;
                if (delta < 0 && phase == Phase.WAITING_RESULT) {
                    pendingPlayerLoss += -delta;
                } else if (delta > 0 && (phase == Phase.ANSWERING || phase == Phase.SHOWING_RESULT)) {
                    playHeal(delta);
                }
            }
            if (monsterMaxHp > 0 && lastMonsterHp >= 0 && monsterHp < lastMonsterHp && phase == Phase.WAITING_RESULT) {
                pendingMonsterLoss += lastMonsterHp - monsterHp;
            }
        }
        lastPlayerHp = playerMaxHp > 0 ? playerHp : -1;
        lastMonsterHp = monsterMaxHp > 0 ? monsterHp : -1;
        playerHearts.setHp(playerHp, playerMaxHp);
        monsterHearts.setHp(monsterHp, monsterMaxHp);
    }

    /**
     * 새 문제를 표시하고 입력 상태와 타이머를 초기화한다. 전투가 끝난 뒤에는 무시한다.
     *
     * @param questionNo       화면에 표시할 문제 순번 (1부터). 시안에 번호 자리가 없어 화면에는 쓰지 않는다
     * @param timeLimitSeconds 제한시간(초). {@link #NO_TIME_LIMIT}(0)이면 타이머 없이 낸다
     */
    public void showQuiz(Quiz quiz, int questionNo, int timeLimitSeconds) {
        if (quiz == null) {
            throw new IllegalArgumentException("quiz is null");
        }
        if (timeLimitSeconds < 0) {
            throw new IllegalArgumentException("timeLimitSeconds must be 0 (no limit) or positive: " + timeLimitSeconds);
        }
        if (phase == Phase.FINISHED) {
            return;
        }
        cancelFeedback();
        inputSubmitButton.setFeedbackColor(null);
        this.currentQuiz = quiz;
        this.timeLimitSeconds = timeLimitSeconds;

        closePopup();
        resetQuestionView();
        stageLabel.setText("STAGE " + (stageNumberOverride > 0 ? stageNumberOverride : quiz.getStageNo()));

        QuestionParts parts = splitQuestion(quiz.getQuestion());
        questionPrompt = parts.prompt();
        questionCode = parts.code();
        if (quiz instanceof ChoiceQuiz choiceQuiz) {
            buildChoices(choiceQuiz.getChoices());
        } else {
            clearChoices();
            inputField.setText("");
        }

        phase = Phase.ANSWERING;
        setInputsEnabled(true);
        applyTextRegion();
        startCountdown();
        syncPhaseUi();
        refitQuestion();
        textScroll.getVerticalScrollBar().setValue(0);
        refreshLayout();
        SwingUtilities.invokeLater(() -> {
            refitQuestion(); // 배치가 끝나 크기가 정해진 뒤 한 번 더 맞춘다
            textScroll.getVerticalScrollBar().setValue(0);
            if (currentQuiz instanceof InputQuiz && popup == null) {
                inputField.requestFocusInWindow();
            } else {
                panel.requestFocusInWindow();
            }
        });
    }

    /**
     * 판정 결과와 해설을 표시한다. 입력과 타이머는 이미 멈춘 상태다.
     * 결과는 문제 글 아래에 이어서 나오고, 보기(입력창)는 숨겨지며 같은 자리에 '다음 문제' 버튼이 나온다.
     *
     * @param detail   결과 아래에 덧붙일 문장 (예: "반격 성공!"). 체력이 몇 줄었는지는 쓰지 않는다. null 가능
     * @param showNext '다음 문제' 버튼을 보일지 여부. 전투가 끝났으면 false
     */
    public void showResult(ResultType type, String detail, boolean showNext) {
        if (currentQuiz == null || phase == Phase.FINISHED) {
            return;
        }
        if (effectsEnabled && phase == Phase.WAITING_RESULT && !feedbackActive) {
            beginFeedback(type); // 채점 결과가 나온 뒤에만 선택한 보기에 색을 칠하고 공격·피격 연출을 시작한다
        }
        defer(() -> doShowResult(type, detail, showNext));
    }

    private void doShowResult(ResultType type, String detail, boolean showNext) {
        if (currentQuiz == null || phase == Phase.FINISHED) {
            return;
        }
        stopCountdown();
        setInputsEnabled(false);

        lastResultType = type;
        String explanation = currentQuiz.getExplanation();
        resultBlock = new ResultBlock(type, detail == null ? "" : detail.strip(), correctAnswerText(currentQuiz),
                type == ResultType.CORRECT ? null
                        : type == ResultType.TIMEOUT ? "제출 없음 (시간 초과)" : submittedAnswerText(currentQuiz, submittedAnswer),
                explanation.isBlank() ? "(해설 없음)" : explanation.strip());
        resultTitle.setText(type.title);
        resultTitle.setForeground(type.color);
        resultDetail.setText(resultBlock.detail());

        nextWanted = showNext;
        nextButton.setEnabled(showNext);
        phase = Phase.SHOWING_RESULT;
        applyTextRegion();
        syncPhaseUi();
        refitQuestion();
        refreshLayout();
        SwingUtilities.invokeLater(() -> {
            refitQuestion();
            refreshLayout();
        });
    }

    /** 전투 종료 표시. 복귀 버튼 문구는 '돌아가기'. */
    public void showBattleEnd(boolean victory, String message) {
        showBattleEnd(victory, message, DEFAULT_RETURN_TEXT);
    }

    /**
     * 전투 종료 표시. 이후 답안 제출과 다음 문제 진행을 모두 막고 복귀 버튼만 보여 준다.
     *
     * @param returnButtonText 복귀 버튼 문구 (예: "돌아가기", "다시 플레이")
     */
    public void showBattleEnd(boolean victory, String message, String returnButtonText) {
        defer(() -> doShowBattleEnd(victory, message, returnButtonText));
    }

    private void doShowBattleEnd(boolean victory, String message, String returnButtonText) {
        stopCountdown();
        setInputsEnabled(false);
        nextWanted = false;
        nextButton.setEnabled(false);
        setEndNote((victory ? "승리! " : "패배... ") + (message == null ? "" : message), INK);
        returnButton.setText(returnButtonText == null || returnButtonText.isBlank()
                ? DEFAULT_RETURN_TEXT : returnButtonText);
        returnWanted = true;
        returnButton.setEnabled(true);
        phase = Phase.FINISHED;
        applyTextRegion();
        syncPhaseUi();
        refreshLayout();
    }

    /**
     * HP 경고 팝업 (계속 싸우기 / 체력 보충하러 가기). 답을 입력하는 중이면 타이머를 멈추고,
     * '계속 싸우기'를 고르면 남은 시간부터 이어 간다. 이미 다른 팝업이 떠 있거나 전투가 끝났으면 무시한다.
     */
    public boolean showLowHpWarning(String message) {
        return showLowHpWarning(message, true);
    }

    /**
     * HP 경고 팝업(회복 아이템 사용 / 계속 싸우기). 회복 아이템이 없으면 팝업을 띄우지 않고 아무 일도 하지 않는다.
     *
     * @param hasRecoveryItem 플레이어가 회복 아이템을 가지고 있는지
     */
    public boolean showLowHpWarning(String message, boolean hasRecoveryItem) {
        if (popup != null || phase == Phase.FINISHED || phase == Phase.READY || !hasRecoveryItem) {
            return false;
        }
        // 정답·오답 연출이 끝난 뒤에 뜬다. 연출 중이면 예약했다는 뜻으로 true를 돌려준다.
        defer(() -> {
            if (popup == null && phase != Phase.FINISHED && phase != Phase.READY) {
                showPopup(PopupType.LOW_HP, "체력 경고!", message,
                        PopupChoice.LOW_HP_USE_ITEM, "회복 아이템 사용", PopupChoice.LOW_HP_CONTINUE, "계속 싸우기");
            }
        });
        return true;
    }

    /**
     * GAME OVER 팝업 (미니게임 / 재시작 / 메인 화면). 전투를 끝내고 모든 입력을 막는다.
     * 결과·해설은 팝업 뒤 문제 글에 남아 있다.
     */
    public void showGameOver(String message) {
        defer(() -> doShowGameOver(message));
    }

    /**
     * 플레이어 HP가 0이 되는 판정. 정답·해설·정답 보기를 알려 주는 결과 화면을 거치지 않고, 오답 색과 피격 연출이 끝난 뒤
     * 곧바로 GAME OVER 팝업을 띄운다 (같은 문제로 다시 도전할 수 있으므로 답을 공개하지 않는다).
     */
    public void showDefeat(ResultType type, String message) {
        if (currentQuiz == null || phase == Phase.FINISHED) {
            return;
        }
        if (effectsEnabled && phase == Phase.WAITING_RESULT && !feedbackActive) {
            beginFeedback(type);
        }
        defer(() -> doShowGameOver(message));
    }

    /**
     * 미니게임에 성공해 살아난 뒤, 죽기 직전의 같은 문제를 다시 낸다. 제한시간은 새로 시작하고 이전 선택·색·연출은 모두 지운다.
     * GAME OVER로 끝난 전투(phase FINISHED)에서만 쓸 수 있다.
     */
    public void resumeQuizAfterRevive(Quiz quiz, int questionNo, int timeLimitSeconds) {
        if (quiz == null || phase != Phase.FINISHED) {
            return;
        }
        cancelFeedback();
        closePopup();
        phase = Phase.READY;
        showQuiz(quiz, questionNo, timeLimitSeconds);
    }

    private void doShowGameOver(String message) {
        stopCountdown();
        setInputsEnabled(false);
        nextWanted = false;
        nextButton.setEnabled(false);
        returnWanted = false;
        returnButton.setEnabled(false);
        setEndNote("GAME OVER" + (message == null || message.isBlank() ? "" : " - " + message), WARNING);
        phase = Phase.FINISHED;
        closePopup();
        showPopup(PopupType.GAME_OVER, "GAME OVER", message,
                PopupChoice.GAME_OVER_MINIGAME, "미니게임",
                PopupChoice.GAME_OVER_RESTART, "재시작",
                PopupChoice.GAME_OVER_MAIN, "메인 화면");
    }

    /** 일시정지 팝업을 연다 (X 버튼·ESC와 같다). 답을 입력하는 중이거나 결과를 보는 중에만 열린다. */
    public void openPause() {
        requestPause();
    }

    private void requestPause() {
        if (popup != null || (phase != Phase.ANSWERING && phase != Phase.SHOWING_RESULT)) {
            return;
        }
        showPopup(PopupType.PAUSE, "일시정지", "타이머가 멈췄습니다. 전투를 중단하면 이 화면을 떠납니다.",
                PopupChoice.PAUSE_RESUME, "계속하기", PopupChoice.PAUSE_ABORT, "전투 중단");
    }

    /** 지금 떠 있는 팝업. 없으면 null */
    public PopupType getPopup() {
        return popup;
    }

    /** 새 전투를 시작할 수 있도록 화면을 처음 상태로 되돌린다. */
    public void resetBattle() {
        stopCountdown();
        cancelFeedback();
        lastPlayerHp = -1;
        lastMonsterHp = -1;
        inputSubmitButton.setFeedbackColor(null);
        closePopup();
        currentQuiz = null;
        phase = Phase.READY;
        resetQuestionView();
        questionPrompt = "";
        questionCode = "";
        clearChoices();
        lastResultType = null;
        stageLabel.setText("STAGE -");
        timerLabel.setText(" ");
        timerLabel.setForeground(INK);
        timeFraction = 1.0;
        placeFire();
        applyTextRegion();
        syncPhaseUi();
        questionText.setParas(List.of());
        refreshLayout();
    }

    public Phase getPhase() {
        return phase;
    }

    public Quiz getCurrentQuiz() {
        return currentQuiz;
    }

    /** 남은 시간(초, 올림). 타이머가 없거나 제한시간 없는 문제면 0 */
    public int getRemainingSeconds() {
        return (int) Math.ceil(remainingMillis() / 1000.0);
    }

    // ---------------------------------------------------------------------
    // Screen 생명주기: 다른 화면으로 나가면 타이머를 멈추고, 돌아오면 남은 시간부터 이어간다.
    // ---------------------------------------------------------------------

    @Override
    public void onEnter() {
        if (popup == null) { // 팝업(일시정지 등)이 떠 있으면 고를 때까지 멈춘 채로 둔다
            resumeCountdown();
        }
    }

    @Override
    public void onExit() {
        pauseCountdown();
        finishEffectsNow(); // 화면을 떠나면 남은 연출 타이머를 정리하고 예약된 화면 갱신은 바로 마친다
        java.util.Arrays.fill(numberHeld, false);
    }

    // ---------------------------------------------------------------------
    // 입력 처리
    // ---------------------------------------------------------------------

    /** 보기 버튼 클릭(또는 숫자 키): 그 보기로 바로 제출한다. */
    private void chooseOption(int index) {
        if (phase != Phase.ANSWERING || popup != null || !(currentQuiz instanceof ChoiceQuiz)
                || index < 0 || index >= choiceButtons.size()) {
            return;
        }
        submit(String.valueOf(index + 1));
    }

    private void submitInput() {
        if (!(currentQuiz instanceof InputQuiz)) {
            return;
        }
        String text = inputField.getText();
        if (text == null || text.isBlank()) {
            inputField.requestFocusInWindow();
            return; // 빈 답은 제출하지 않는다
        }
        submit(text);
    }

    /** 답안 제출. ANSWERING 단계에서 한 번만 리스너로 전달된다 (중복 제출 방지). */
    private void submit(String answer) {
        if (phase != Phase.ANSWERING || currentQuiz == null || popup != null) {
            return;
        }
        submittedAnswer = answer;
        lockForJudging();
        if (listener != null) {
            listener.onAnswerSubmitted(currentQuiz, answer);
        }
    }

    private void requestNext() {
        if (phase != Phase.SHOWING_RESULT || popup != null) {
            return;
        }
        phase = Phase.WAITING_RESULT;
        nextButton.setEnabled(false);
        syncPhaseUi();
        if (listener != null) {
            listener.onNextRequested();
        }
    }

    /** 전투 종료 후 복귀 버튼. FINISHED 단계에서 한 번만 리스너로 전달된다. */
    private void requestReturn() {
        if (phase != Phase.FINISHED || !returnButton.isEnabled() || popup != null) {
            return;
        }
        returnButton.setEnabled(false);
        if (listener != null) {
            listener.onReturnRequested();
        }
    }

    private void lockForJudging() {
        phase = Phase.WAITING_RESULT;
        stopCountdown();
        setInputsEnabled(false);
        syncPhaseUi();
    }

    // ---------------------------------------------------------------------
    // 팝업
    // ---------------------------------------------------------------------

    /** 팝업을 연다. 선택지는 1~3개(순서대로). 타이머는 멈추고 선택하면 이어서 센다. */
    private void showPopup(PopupType type, String title, String message, Object... choicesAndTexts) {
        pauseCountdown();
        popup = type;
        SketchButton[] buttons = {popupPrimary, popupSecondary, popupTertiary};
        PopupChoice[] choices = new PopupChoice[3];
        popupButtonCount = Math.min(3, choicesAndTexts.length / 2);
        for (int i = 0; i < popupButtonCount; i++) {
            choices[i] = (PopupChoice) choicesAndTexts[i * 2];
            buttons[i].setText((String) choicesAndTexts[i * 2 + 1]);
            buttons[i].setEnabled(true);
        }
        popupPrimaryChoice = choices[0];
        popupSecondaryChoice = choices[1];
        popupTertiaryChoice = choices[2];
        popupTitle.setText(title);
        popupTitle.setForeground(type == PopupType.PAUSE ? INK : WARNING);
        popupMessage.setText(message == null ? "" : message);
        applyPopupLayout();
        syncPhaseUi();
        refreshLayout();
    }

    private void closePopup() {
        popup = null;
        popupPrimaryChoice = null;
        popupSecondaryChoice = null;
        popupTertiaryChoice = null;
        popupButtonCount = 0;
        syncVisibility();
    }

    /** 팝업 버튼 선택. 지금 떠 있는 팝업의 선택만, 한 번만 받는다. */
    private void choose(PopupChoice choice) {
        if (choice == null || popup != choice.popup()) {
            return;
        }
        popupPrimary.setEnabled(false);
        popupSecondary.setEnabled(false);
        popupTertiary.setEnabled(false);
        closePopup();
        if (choice.continuesBattle()) {
            resumeCountdown();
            syncPhaseUi();
            if (phase == Phase.ANSWERING && currentQuiz instanceof InputQuiz) {
                SwingUtilities.invokeLater(inputField::requestFocusInWindow);
            } else {
                SwingUtilities.invokeLater(panel::requestFocusInWindow);
            }
        } else {
            leaveBattle(choice);
        }
        refreshLayout();
        if (listener != null) {
            listener.onPopupChoice(choice);
        }
    }

    /** 화면을 떠나는 선택 뒤: 더 이상 입력·진행을 받지 않는다. 실제 이동은 리스너가 한다. */
    private void leaveBattle(PopupChoice choice) {
        stopCountdown();
        setInputsEnabled(false);
        nextWanted = false;
        nextButton.setEnabled(false);
        returnWanted = false;
        returnButton.setEnabled(false);
        String text = switch (choice) {
            case PAUSE_ABORT -> "전투를 중단했습니다.";
            case LOW_HP_RECOVER -> "체력을 보충하러 갑니다.";
            case GAME_OVER_MINIGAME -> "미니게임으로 이동합니다.";
            case GAME_OVER_MAIN -> "메인 화면으로 돌아갑니다.";
            case GAME_OVER_RESTART -> "스테이지를 다시 시작합니다.";
            case GAME_OVER_STAGE_SELECT -> "스테이지 선택으로 돌아갑니다.";
            default -> "";
        };
        setEndNote(text, INK);
        phase = Phase.FINISHED;
        applyTextRegion();
        syncPhaseUi();
    }

    // ---------------------------------------------------------------------
    // 상태에 따른 화면 표시
    // ---------------------------------------------------------------------

    /** 단계(phase)가 바뀔 때마다 부르는 한 곳: X 버튼 사용 여부와 각 요소의 보임 여부를 맞춘다. */
    private void syncPhaseUi() {
        boolean available = popup == null && (phase == Phase.ANSWERING || phase == Phase.SHOWING_RESULT);
        pauseButton.setEnabled(available);
        syncVisibility();
    }

    /** 지금 상태에서 무엇이 보여야 하는지를 한 곳에서 정한다. 팝업이 뜨면 문제와 보기는 숨는다. */
    private void syncVisibility() {
        boolean popupOn = popup != null;
        boolean choiceQuiz = currentQuiz instanceof ChoiceQuiz;
        boolean inputQuiz = currentQuiz instanceof InputQuiz;
        boolean answerPhase = phase == Phase.ANSWERING || phase == Phase.WAITING_RESULT;
        boolean resultOn = !popupOn && (phase == Phase.SHOWING_RESULT || (phase == Phase.FINISHED && resultBlock != null));

        textScroll.setVisible(!popupOn);
        for (SketchButton b : choiceButtons) {
            b.setVisible(!popupOn && choiceQuiz && answerPhase);
        }
        boolean inputOn = !popupOn && inputQuiz && answerPhase;
        inputFrame.setVisible(inputOn);
        inputField.setVisible(inputOn);
        inputSubmitButton.setVisible(inputOn);
        inputHint.setVisible(inputOn);
        nextButton.setVisible(!popupOn && phase == Phase.SHOWING_RESULT && nextWanted);
        returnButton.setVisible(!popupOn && phase == Phase.FINISHED && returnWanted);

        resultTitle.setVisible(resultOn);
        resultDetail.setVisible(resultOn && resultBlock != null && !resultBlock.detail().isEmpty());
        divider.setVisible(resultOn || popupOn);
        layout.setSpec(divider, popupOn ? popupDividerSpec : DIVIDER);

        popupTitle.setVisible(popupOn);
        popupMessage.setVisible(popupOn);
        popupPrimary.setVisible(popupOn && popupButtonCount >= 1);
        popupSecondary.setVisible(popupOn && popupButtonCount >= 2);
        popupTertiary.setVisible(popupOn && popupButtonCount >= 3);
        syncTimerUi();
        stage.repaint();
    }

    /**
     * 상단 타이머 표시를 상태에 맞춘다.
     * 풀이 중: TIME n + 남은 시간 위치의 불꽃. 일시정지: PAUSED + 흐린 불꽃(멈춤). HP 경고 팝업이 풀이 중 떠 있으면 시간이 멈춘
     * 채로 흐린 불꽃. 결과·전투 종료·GAME OVER: TIME — 와 함께 불꽃을 숨긴다.
     */
    private void syncTimerUi() {
        boolean gameOver = popup == PopupType.GAME_OVER;
        timerLabel.setVisible(!gameOver);
        timerLine.setVisible(!gameOver);
        if (gameOver) {
            timerLabel.setText(" ");
            fire.setVisible(false);
        } else if (popup == PopupType.PAUSE) {
            timerLabel.setText("PAUSED");
            timerLabel.setForeground(INK);
            fire.setVisible(phase == Phase.ANSWERING);
            fire.setAlpha(0.35f);
        } else if (phase == Phase.ANSWERING) {
            updateTimerDisplay(remainingMillis());
            fire.setVisible(timeLimitSeconds != NO_TIME_LIMIT);
            fire.setAlpha(popup != null ? 0.35f : 1f);
        } else if (phase == Phase.READY) {
            timerLabel.setText(" ");
            timerLine.set(0, false);
            fire.setVisible(false);
        } else {
            timerLabel.setText("TIME —");
            timerLabel.setForeground(INK);
            if (phase != Phase.WAITING_RESULT) {
                timerLine.set(0, false); // 결과·종료 화면: 남은 시간 표시는 없고 바탕 선만 보인다. 판정 중에는 멈춘 모습 그대로
            }
            fire.setVisible(false);
        }
    }

    private void setInputsEnabled(boolean enabled) {
        for (SketchButton b : choiceButtons) {
            b.setEnabled(enabled);
        }
        inputField.setEnabled(enabled);
        inputField.setEditable(enabled);
        inputSubmitButton.setEnabled(enabled);
    }

    private void resetQuestionView() {
        submittedAnswer = null;
        inputField.setText("");
        resultBlock = null;
        endNote = null;
        nextWanted = false;
        returnWanted = false;
        nextButton.setEnabled(false);
        returnButton.setEnabled(false);
    }

    private void setEndNote(String text, Color color) {
        endNote = text;
        endColor = color;
        refitQuestion();
    }

    // ---------------------------------------------------------------------
    // 보기 / 배치
    // ---------------------------------------------------------------------

    private void buildChoices(List<String> choices) {
        clearChoices();
        for (int i = 0; i < choices.size(); i++) {
            int index = i;
            SketchButton b = new SketchButton(assets, SketchAssets.BUTTON_WIDE, choices.get(i), () -> chooseOption(index));
            b.setName("choice" + (i + 1));
            b.setToolTipText(choices.get(i));
            b.setShrinkToFit(false); // 같은 문제의 보기는 모두 같은 글자 크기: 보기마다 따로 줄이지 않는다
            b.setLeadingLabel(String.valueOf(i + 1)); // 왼쪽의 작은 번호 (숫자 키 1~4와 같은 번호)
            choiceButtons.add(b);
            stage.add(b, SketchLayout.center(0, 0, 10, 10), 0);
        }
        applyChoiceLayout();
    }

    private void clearChoices() {
        for (SketchButton b : choiceButtons) {
            stage.remove(b);
        }
        choiceButtons.clear();
    }

    /**
     * 이 문제의 보기를 어떤 배치로 보여 줄지.
     *
     * 기본은 자동(AUTO)이다: 네 보기가 모두 기본 글자 크기(24px)로 2×2 버튼 안쪽 칸(버튼 폭에서 여백과 번호 칸을 뺀 폭)에 한 줄로
     * 들어가면 2×2(GRID), 하나라도 안 들어가면 가로 4줄(WIDE)이다. 폭은 실제 적용 글꼴의 FontMetrics로 재며,
     * 2×2를 만들려고 글자를 줄이지 않는다. 문제를 낼 때와 창 크기가 바뀔 때마다 다시 판단한다.
     * {@link #setChoiceLayout}으로 WIDE/GRID를 지정하면 그 배치로 고정된다(데모·테스트용).
     */
    private ChoiceLayout layoutFor(ChoiceQuiz quiz) {
        if (choiceLayout != ChoiceLayout.AUTO) {
            return choiceLayout;
        }
        double s = scaleNow();
        return maxChoiceLines(quiz.getChoices(), FONT_CHOICE_MAX * s, choiceInnerWidth(true, FONT_CHOICE_MAX, s)) == 1
                ? ChoiceLayout.GRID : ChoiceLayout.WIDE;
    }

    /** 보기 본문이 쓸 수 있는 폭(px): 버튼 폭에서 좌우 여백과 왼쪽 번호 칸(글꼴 크기에 비례)을 뺀 값 */
    private double choiceInnerWidth(boolean grid, double fontPx, double s) {
        double btnW = grid ? GRID_W : CW;
        return (btnW * (1 - 2 * (grid ? GRID_PAD_X : WIDE_PAD_X)) - fontPx * SketchButton.LEADING_RATIO) * s;
    }

    /** 지금 문제에 실제로 적용된 보기 배치 (자동 선택 결과 포함). 객관식이 아니면 null. */
    public ChoiceLayout getAppliedChoiceLayout() {
        return currentQuiz instanceof ChoiceQuiz cq ? layoutFor(cq) : null;
    }

    /** 보기 배치(WIDE/GRID)에 맞춰 각 보기 버튼의 이미지·여백을 정하고 문제 화면 배치를 다시 한다. 정답 처리는 두 배치가 같다. */
    private void applyChoiceLayout() {
        boolean grid = currentQuiz instanceof ChoiceQuiz cq && layoutFor(cq) == ChoiceLayout.GRID;
        for (SketchButton b : choiceButtons) {
            b.setImageFile(grid ? SketchAssets.BUTTON_SQUARE : SketchAssets.BUTTON_WIDE);
            b.setPaddingRatio(grid ? GRID_PAD_X : WIDE_PAD_X, grid ? GRID_PAD_Y : WIDE_PAD_Y);
        }
        planAndApplyQuestionLayout();
    }

    /**
     * 팝업의 배치: 제목 → 구분선 → 안내 문구 → 버튼을 간격을 좁혀 이어 붙이고, 이 묶음 전체를 문제 틀 안쪽 가운데에 둔다
     * (안내 문구와 버튼 사이에 큰 빈 공간이 생기지 않는다). 버튼은 3개면 가로 줄 쌓기, 1~2개면 정사각 버튼 줄이며
     * 글자 크기는 모두 같다.
     */
    private void applyPopupLayout() {
        double s = scaleNow();
        SketchButton[] buttons = {popupPrimary, popupSecondary, popupTertiary};
        boolean stacked = popupButtonCount >= 3;
        double titleH = 58;
        double dividerGap = 14;
        double afterDivider = 18;
        double buttonGap = 30;
        double buttonBlock = stacked ? 3 * WIDE_H + 2 * WIDE_GAP : GRID_H;
        int lines = 0;
        String msg = popupMessage.getText();
        if (msg != null && !msg.isBlank()) {
            FontMetrics fm = popupMessage.getFontMetrics(GameFonts.text(Font.PLAIN, (float) (FONT_POPUP_MESSAGE * s)));
            lines = SketchText.wrap(msg, fm, (int) Math.round(CW * s)).size();
        }
        double msgH = lines == 0 ? 0 : lines * Math.round(FONT_POPUP_MESSAGE * 1.4) + 6;
        double total = titleH + dividerGap + 6 + afterDivider + msgH + (msgH > 0 ? buttonGap : buttonGap - afterDivider) + buttonBlock;
        double top = Math.max(CY0, (CY0 + CY1) / 2 - total / 2);
        double divY = top + titleH + dividerGap;
        double msgY = divY + 6 + afterDivider;
        double btnY = msgY + msgH + (msgH > 0 ? buttonGap : buttonGap - afterDivider);
        layout.setSpec(popupTitle, SketchLayout.center(CX, top, CW, titleH));
        popupDividerSpec = SketchLayout.center(CX, divY, CW, 6);
        layout.setSpec(divider, popupDividerSpec);
        layout.setSpec(popupMessage, SketchLayout.center(CX, msgY, CW, Math.max(1, msgH)));
        for (int i = 0; i < popupButtonCount; i++) {
            SketchButton b = buttons[i];
            b.setShrinkToFit(false);
            if (stacked) {
                b.setImageFile(SketchAssets.BUTTON_WIDE);
                b.setPaddingRatio(WIDE_PAD_X, WIDE_PAD_Y);
                layout.setSpec(b, SketchLayout.center(CX, btnY + i * (WIDE_H + WIDE_GAP), CW, WIDE_H));
            } else {
                b.setImageFile(SketchAssets.BUTTON_SQUARE);
                b.setPaddingRatio(GRID_PAD_X, GRID_PAD_Y);
                double x = popupButtonCount == 1 ? 640 - GRID_W / 2 : (i == 0 ? CX : CX + GRID_W + GRID_COL_GAP);
                layout.setSpec(b, SketchLayout.center(x, btnY, GRID_W, GRID_H));
            }
            b.setFont(GameFonts.label((float) (FONT_POPUP_BUTTON * s)));
        }
    }

    /** 이전 버전과의 호환용: 문제 글 영역은 {@link #refitQuestion()}이 문제·결과 상태에 맞춰 정한다. */
    private void applyTextRegion() {
        // 위치와 크기는 refitQuestion에서 한꺼번에 정한다
    }

    /** 문제 화면의 배치 계획 결과 (좌표는 1280×720 기준) */
    record QuestionPlan(double prose, double code, double choicePx, int choiceLines, double rowH, double textH,
                        double regionH, double textY, double blockY, FitStatus status) {
    }

    /** 문제와 보기가 한 화면에 들어갔는지 */
    public enum FitStatus {
        /** 문제와 보기가 모두 들어가고 스크롤도 줄바꿈된 코드도 없다 */
        FITS,
        /** 들어가지만 코드 줄이 칸보다 길어 이어서 표시했다 */
        CODE_WRAPPED,
        /** 가장 작은 글자 크기로도 안 들어가 문제 글이 스크롤된다 */
        SCROLLS
    }

    private QuestionPlan questionPlan;

    /** 지금 문제 화면의 배치 계획 (검증용). 문제를 풀이 중이 아니면 null. */
    QuestionPlan getQuestionPlan() {
        return questionPlan;
    }

    /** 문제가 얼마나 잘 들어갔는지 (검증용) */
    public FitStatus getFitStatus() {
        if (phase == Phase.SHOWING_RESULT || phase == Phase.FINISHED) {
            return resultFit;
        }
        return questionPlan == null ? null : questionPlan.status();
    }

    /** 결과 화면 해설 글자 크기(1280×720 기준 px, 검증용) */
    double getResultBodyPx() {
        return resultBodyPx;
    }

    /**
     * 풀이 중 화면(문제 글 + 보기 또는 입력창)의 크기와 위치를 정한다.
     *
     * 문제 글은 언제나 안전 영역 맨 위(Q. 시작 위치 고정)에서 시작하고, 높이는 실제 줄바꿈·글꼴·줄 간격·코드 상자를
     * 잰 값 그대로다(문제가 짧다고 가운데로 옮기지 않는다). 보기·입력창은 그 바로 아래 간격(기본 22px)을 두고 이어진다.
     *
     * 큰 글자 단계부터 시험해 문제와 보기가 함께 들어가는 첫 단계를 쓰고(코드 줄바꿈 없는 경우를 먼저), 없으면 가장 작은
     * 단계로 두되 문제 글만 스크롤한다(예외적인 안전장치). 보기 버튼 높이와 글자 크기는 줄이지 않는다.
     */
    private void planAndApplyQuestionLayout() {
        if (currentQuiz == null || (phase != Phase.ANSWERING && phase != Phase.WAITING_RESULT)) {
            questionPlan = null;
            return;
        }
        // 문제 글을 틀의 왼쪽 위 모서리에서 조금 띄운다(오른쪽 12px, 아래 10px). 그 때문에 글자 단계가 내려가거나 코드가
        // 새로 줄바꿈되면 이동량을 줄이고, 그래도 안 되면 원래 위치를 쓴다 (글꼴을 줄이지도 틀을 넓히지도 않는다).
        QuestionPlan base = planQuestion(0, 0, false);
        double[][] offsets = {{Q_OFFSET_X, Q_OFFSET_Y}, {8, 6}, {4, 3}};
        for (double[] o : offsets) {
            QuestionPlan p = planQuestion(o[0], o[1], false);
            if (p != null && base != null && p.prose() == base.prose() && p.code() == base.code() && p.status() == base.status()
                    && p.rowH() >= base.rowH() - 0.01) {
                planQuestion(o[0], o[1], true);
                return;
            }
        }
        planQuestion(0, 0, true);
    }

    /** 문제 글 시작 위치를 (dx, dy)만큼 옮겼을 때의 배치 계획. commit이면 화면에 적용한다. */
    private QuestionPlan planQuestion(double dx, double dy, boolean commit) {
        double s = scaleNow();
        final double chAvail = CH - dy;
        boolean choiceQuiz = currentQuiz instanceof ChoiceQuiz;
        boolean grid = choiceQuiz && layoutFor((ChoiceQuiz) currentQuiz) == ChoiceLayout.GRID;
        int n = choiceButtons.size();

        // 보기: 한 문제의 보기는 모두 같은 글자 크기. 모두 한 줄에 들어가는 가장 큰 크기(최대 24, 최소 20)를 쓴다.
        double choicePx = FONT_CHOICE_MIN;
        int maxLines = 1;
        if (choiceQuiz) {
            List<String> texts = ((ChoiceQuiz) currentQuiz).getChoices();
            boolean found = false;
            for (double c = FONT_CHOICE_MAX; c >= FONT_CHOICE_MIN - 1e-6; c -= 1) {
                if (maxChoiceLines(texts, c * s, choiceInnerWidth(grid, c, s)) == 1) {
                    choicePx = c;
                    found = true;
                    break;
                }
            }
            if (!found) { // 20px로도 한 줄에 안 들어가는 보기: 줄바꿈하고 모든 버튼을 그만큼 높인다
                maxLines = maxChoiceLines(texts, FONT_CHOICE_MIN * s, choiceInnerWidth(grid, FONT_CHOICE_MIN, s));
            }
        }
        double baseRow = grid ? GRID_H : WIDE_H;
        if (maxLines > 1) {
            double padY = grid ? GRID_PAD_Y : WIDE_PAD_Y;
            baseRow = Math.max(baseRow, Math.ceil(maxLines * Math.ceil(choicePx * 1.3) / (1 - 2 * padY)));
        }
        int rows = grid ? (n + 1) / 2 : n;
        double rowGap = grid ? GRID_ROW_GAP : WIDE_GAP;
        double rowH = baseRow;
        double blockH = choiceQuiz ? rows * rowH + Math.max(0, rows - 1) * rowGap : INPUT_H + INPUT_GAP + SUBMIT_H + HINT_GAP + HINT_H;

        // 글자 단계 고르기: 큰 단계부터, 코드 줄바꿈 없는 경우를 먼저
        List<SketchRichText.Para> paras = questionParas();
        int widthPx = (int) Math.round((CW - dx) * s) - (dx > 0 ? 3 : 0); // 줄인 폭에서 반올림 오차 여유 3px
        int pick = -1;
        double pickText = 0;
        FitStatus status = FitStatus.FITS;
        for (int pass = 0; pass < 2 && pick < 0; pass++) {
            for (int t = 0; t < QUESTION_TIERS.length && pick < 0; t++) {
                SketchRichText.Measure m = SketchRichText.measure(paras, widthPx, tierSizes(t, s));
                if (pass == 0 && m.codeWrapped()) {
                    continue;
                }
                double h = m.height() / s;
                if (h + MIN_GAP + blockH <= chAvail + 0.5) {
                    pick = t;
                    pickText = h;
                    status = m.codeWrapped() ? FitStatus.CODE_WRAPPED : FitStatus.FITS;
                }
            }
        }
        double regionH;
        double gap;
        double shift = 0;
        if (pick < 0) { // 가장 작은 단계로도 안 들어간다: 문제 글만 스크롤 (예외적인 안전장치)
            pick = QUESTION_TIERS.length - 1;
            pickText = SketchRichText.measure(paras, widthPx, tierSizes(pick, s)).height() / s;
            gap = MIN_GAP;
            regionH = Math.max(40, chAvail - gap - blockH);
            status = FitStatus.SCROLLS;
        } else {
            regionH = pickText;
            gap = Math.min(PREF_GAP, chAvail - regionH - blockH);
            // 남는 공간이 충분할 때만(긴 문제는 그대로): 보기 버튼을 권장 높이까지 키우고, 행 간격과 문제-보기 간격을
            // 조금 늘리고, 보기 묶음을 약간 아래로 내린다. 문제-보기 간격은 총 46px(+아래로 내린 만큼)을 넘지 않는다.
            double leftover = chAvail - regionH - gap - blockH;
            if (choiceQuiz && maxLines == 1 && rows > 0) {
                double grow = Math.min((grid ? GRID_H_MAX : WIDE_H_MAX) - rowH, leftover / rows);
                if (grow > 0) {
                    rowH += grow;
                    blockH += grow * rows;
                    leftover -= grow * rows;
                }
            }
            if (choiceQuiz && rows > 1 && leftover > 0) {
                double more = Math.min(grid ? 28 : 10, leftover * 0.5 / (rows - 1));
                rowGap += more;
                blockH += more * (rows - 1);
                leftover -= more * (rows - 1);
            }
            if (leftover > 0) {
                double extra = Math.min(24, leftover * 0.5);
                gap += extra;
                leftover -= extra;
                shift = Math.min(choiceQuiz ? 44 : 44 + INPUT_EXTRA_DROP, leftover * 0.8);
            }
        }
        double textY = CY0 + dy;
        double blockY = textY + regionH + gap + shift;
        QuestionPlan plan = new QuestionPlan(QUESTION_TIERS[pick][0], QUESTION_TIERS[pick][1], choicePx, maxLines, rowH,
                pickText, regionH, textY, blockY, status);
        if (!commit) {
            return plan;
        }
        questionPlan = plan;

        // 적용
        questionText.setSizes(tierSizes(pick, s));
        questionText.setParas(paras);
        textScroll.setVerticalScrollBarPolicy(status == FitStatus.SCROLLS
                ? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS : JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        double regionPad = status == FitStatus.SCROLLS ? 0 : 3; // 글자 아랫부분이 잘리지 않게 약간 여유
        layout.setSpec(textScroll, SketchLayout.center(CX + dx, textY, CW - dx, regionH + regionPad));
        if (choiceQuiz) {
            Font cf = GameFonts.text(Font.PLAIN, (float) (choicePx * s));
            for (int i = 0; i < n; i++) {
                SketchButton b = choiceButtons.get(i);
                b.setFont(cf);
                if (grid) {
                    double x = CX + (i % 2) * (GRID_W + GRID_COL_GAP);
                    layout.setSpec(b, SketchLayout.center(x, blockY + (i / 2) * (rowH + rowGap), GRID_W, rowH));
                } else {
                    layout.setSpec(b, SketchLayout.center(CX, blockY + i * (rowH + rowGap), CW, rowH));
                }
            }
        } else {
            layout.setSpec(inputFrame, SketchLayout.center(CX, blockY, CW, INPUT_H));
            layout.setSpec(inputField, SketchLayout.center(CX + 24, blockY + 4, CW - 48, INPUT_H - 8));
            layout.setSpec(inputSubmitButton, SketchLayout.center(640 - ACTION_W / 2, blockY + INPUT_H + INPUT_GAP, ACTION_W, SUBMIT_H));
            layout.setSpec(inputHint, SketchLayout.center(CX, blockY + INPUT_H + INPUT_GAP + SUBMIT_H + HINT_GAP, CW, HINT_H));
        }
        return plan;
    }

    private SketchRichText.Sizes tierSizes(int tier, double s) {
        return new SketchRichText.Sizes(QUESTION_TIERS[tier][0] * s, QUESTION_TIERS[tier][1] * s, FONT_BODY_MIN * s,
                QUESTION_TIERS[tier][2]);
    }

    /** texts를 한 줄 폭 width(px)에 글꼴 크기 px로 그릴 때 가장 많이 줄바꿈되는 보기의 줄 수 */
    private int maxChoiceLines(List<String> texts, double px, double width) {
        Font font = GameFonts.text(Font.PLAIN, (float) px);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        try {
            FontMetrics fm = g2.getFontMetrics(font);
            int max = 1;
            for (String t : texts) {
                int lines = fm.stringWidth(t) <= width ? 1 : wrapLines(t, fm, (int) Math.floor(width));
                max = Math.max(max, lines);
            }
            return max;
        } finally {
            g2.dispose();
        }
    }

    private static int wrapLines(String text, FontMetrics fm, int width) {
        return SketchText.wrap(text, fm, width).size();
    }

    /**
     * 결과 화면의 배치: 판정 문구(초록/빨강) → HP 변동 → 구분선 → 정답·해설 → 다음 문제(또는 돌아가기) 버튼.
     * 문제 본문과 코드는 다시 쓰지 않는다. 해설 글자는 22px에서 시작해 20px까지 줄이고, 그래도 넘치면 스크롤한다.
     */
    private void applyResultLayout() {
        double s = scaleNow();
        boolean hasAction = (phase == Phase.SHOWING_RESULT && nextWanted) || (phase == Phase.FINISHED && returnWanted);
        boolean hasResult = resultBlock != null;
        double bodyY = hasResult ? RESULT_BODY_Y : CY0;
        double bodyBottom = hasAction ? ACTION_Y - 12 : CY1;
        double regionH = bodyBottom - bodyY;
        List<SketchRichText.Para> paras = resultParas();
        int widthPx = (int) Math.round(CW * s);
        double px = FONT_BODY_MIN;
        FitStatus status = FitStatus.SCROLLS;
        for (double c = FONT_BODY_MAX; c >= FONT_BODY_MIN - 1e-6; c -= 1) {
            SketchRichText.Measure m = SketchRichText.measure(paras, widthPx, new SketchRichText.Sizes(c * s, 0, c * s));
            if (m.height() / s <= regionH) {
                px = c;
                status = FitStatus.FITS;
                break;
            }
        }
        questionPlan = null;
        questionText.setSizes(new SketchRichText.Sizes(px * s, FONT_BODY_MIN * s, px * s));
        questionText.setParas(paras);
        resultBodyPx = px;
        resultFit = status;
        textScroll.setVerticalScrollBarPolicy(status == FitStatus.SCROLLS
                ? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS : JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        layout.setSpec(textScroll, SketchLayout.center(CX, bodyY, CW, regionH));
        layout.setSpec(resultTitle, RESULT_TITLE);
        layout.setSpec(resultDetail, RESULT_DETAIL);
        if (popup == null) { // 팝업이 떠 있으면 구분선은 팝업 배치(popupDividerSpec)를 따른다
            layout.setSpec(divider, DIVIDER);
        }
    }

    /** 결과 본문: 정답 · 해설 · (전투 종료 문구) */
    private List<SketchRichText.Para> resultParas() {
        List<SketchRichText.Para> list = new ArrayList<>();
        if (resultBlock != null) {
            list.add(new SketchRichText.Para(SketchRichText.Kind.BOX, "정답 : " + resultBlock.answer(), OK_TEXT, OK_BG));
            if (resultBlock.userAnswer() != null) {
                String label = currentQuiz instanceof ChoiceQuiz ? "내가 고른 답 : " : "내가 쓴 답 : ";
                list.add(new SketchRichText.Para(SketchRichText.Kind.BOX, label + resultBlock.userAnswer(), BAD_TEXT, BAD_BG));
            }
            if (!resultBlock.explanation().isEmpty()) {
                list.add(new SketchRichText.Para(SketchRichText.Kind.BOLD, "해설", INK));
                list.add(new SketchRichText.Para(SketchRichText.Kind.BODY, resultBlock.explanation(), INK));
            }
        }
        if (endNote != null) {
            list.add(new SketchRichText.Para(SketchRichText.Kind.BOLD, endNote, endColor));
        }
        return list;
    }

    /** 지금 상태(문제 풀이 / 결과)에 맞게 문제 글 영역의 글자 크기와 위치를 정한다. */
    private void refitQuestion() {
        if (stage.getWidth() <= 0) {
            return;
        }
        if (phase == Phase.SHOWING_RESULT || phase == Phase.FINISHED) {
            applyResultLayout();
        } else if (currentQuiz != null) {
            planAndApplyQuestionLayout();
        } else {
            questionText.setParas(List.of());
        }
        stage.revalidate();
    }

    private List<SketchRichText.Para> questionParas() {
        List<SketchRichText.Para> list = new ArrayList<>();
        if (currentQuiz != null) {
            list.add(new SketchRichText.Para(SketchRichText.Kind.PROMPT, questionPrompt, INK));
            if (!questionCode.isEmpty()) {
                list.add(new SketchRichText.Para(SketchRichText.Kind.CODE, questionCode, CODE_INK));
            }
        }
        return list;
    }

    private double scaleNow() {
        if (appliedScale > 0) {
            return appliedScale;
        }
        return stage.getWidth() > 0 && stage.getHeight() > 0 ? SketchLayout.scaleFor(stage.getWidth(), stage.getHeight()) : 1.0;
    }

    /** 화면 배율이 바뀌면(창 크기 변경) 모든 글꼴과 문제 글자 크기를 다시 맞춘다. SketchLayout이 배치 때 부른다. */
    private void applyScale(double s) {
        if (Math.abs(s - appliedScale) < 0.0005) {
            return;
        }
        appliedScale = s;
        stageLabel.setFont(GameFonts.hand((float) (FONT_STAGE * s)));
        timerLabel.setFont(GameFonts.hand((float) (FONT_TIME * s)));
        escLabel.setFont(GameFonts.hand((float) (FONT_ESC * s)));
        playerName.setFont(GameFonts.hand((float) (FONT_NAME * s)));
        monsterName.setFont(GameFonts.hand((float) (FONT_NAME * s)));
        playerHearts.setFont(GameFonts.hand((float) (FONT_HP * s)));
        monsterHearts.setFont(GameFonts.hand((float) (FONT_HP * s)));
        popupTitle.setFont(GameFonts.title((float) (FONT_POPUP_TITLE * s)));
        popupMessage.setFont(GameFonts.text(Font.PLAIN, (float) (FONT_POPUP_MESSAGE * s)));
        resultTitle.setFont(GameFonts.title((float) (FONT_RESULT_TITLE * s)));
        resultDetail.setFont(GameFonts.text(Font.PLAIN, (float) (FONT_RESULT_DETAIL * s)));
        inputHint.setFont(GameFonts.label((float) (FONT_HINT * s)));
        int pad = (int) Math.round(CHAR_PAD * s);
        playerView.setPad(pad);
        monsterView.setPad(pad);
        inputField.setFont(GameFonts.text(Font.PLAIN, (float) (FONT_INPUT * s)));
        inputField.setHintFont(GameFonts.text(Font.PLAIN, (float) (FONT_INPUT * s)));
        Font action = GameFonts.label((float) (FONT_ACTION * s));
        inputSubmitButton.setFont(action);
        nextButton.setFont(action);
        returnButton.setFont(action);
        applyChoiceLayout();
        applyPopupLayout();
        // 배율이 바뀐 뒤 문제 글자 크기를 새 영역에 맞춰 다시 고른다
        SwingUtilities.invokeLater(() -> {
            refitQuestion();
            placeFire();
        });
    }

    /** 지금 상태의 상단 문구 (TIME n / PAUSED / TIME —) — 검증용 */
    String timerText() {
        return timerLabel.getText();
    }

    boolean isFireVisible() {
        return fire.isVisible();
    }

    private void refreshLayout() {
        stage.revalidate();
        stage.repaint();
    }

    // ---------------------------------------------------------------------
    // 연출 (정답·오답 색, 공격·피격 애니메이션, 피해량·회복량). 판정·HP·타이머는 건드리지 않는다.
    // ---------------------------------------------------------------------

    /** false면 연출 없이 결과를 곧바로 보여 준다 (테스트·성능이 낮은 환경용). 기본 true. */
    public void setEffectsEnabled(boolean enabled) {
        if (!enabled) {
            finishEffectsNow();
        }
        this.effectsEnabled = enabled;
    }

    public boolean isEffectsEnabled() {
        return effectsEnabled;
    }

    /** 제출 직후 색을 보여 주는 중인지 (이 동안 결과 화면 전환은 잠깐 미뤄진다) */
    public boolean isFeedbackActive() {
        return feedbackActive;
    }

    /** 연출이 진행 중인지: 공격·피격·회복 움직임 또는 떠오르는 숫자가 남아 있다 */
    public boolean isEffectRunning() {
        return fxTimer.isRunning() || !anims.isEmpty() || !floaters.isEmpty();
    }

    /** 지금 떠 있는 피해량·회복량 문구들 (검증용) */
    List<String> floatingTexts() {
        List<String> out = new ArrayList<>();
        for (Floater f : floaters) {
            out.add(f.text());
        }
        return out;
    }

    /** 캐릭터의 현재 가로 이동 px (검증용) */
    double characterOffset(boolean player) {
        return (player ? playerView : monsterView).getDx();
    }

    double characterFlash(boolean player) {
        return (player ? playerView : monsterView).getFlash();
    }

    double characterGlow(boolean player) {
        return (player ? playerView : monsterView).getGlow();
    }

    /** 타이머 선의 남은 비율과 경고(빨강) 여부 (검증용) */
    double timerBarFraction() {
        return timerLine.getFraction();
    }

    boolean timerBarWarning() {
        return timerLine.isWarning();
    }

    boolean isCountdownRunning() {
        return countdown.isRunning();
    }

    /** 화면 갱신을 연출이 끝난 뒤로 미룬다. 연출 중이 아니면 바로 실행한다. */
    private void defer(Runnable action) {
        if (feedbackActive) {
            deferred.add(action);
        } else {
            action.run();
        }
    }

    private void beginFeedback(ResultType type) {
        feedbackActive = true;
        Color color = type == ResultType.CORRECT ? FEEDBACK_OK : type == ResultType.WRONG ? FEEDBACK_BAD : null;
        if (color != null) {
            if (currentQuiz instanceof ChoiceQuiz) {
                int index = chosenIndex();
                if (index >= 0 && index < choiceButtons.size()) {
                    choiceButtons.get(index).setFeedbackColor(color);
                }
            } else {
                inputSubmitButton.setFeedbackColor(color);
            }
        }
        playBattleFx(type);
        feedbackTimer.restart();
    }

    /** 제출한 보기의 0부터 시작하는 번호. 없으면 -1 */
    private int chosenIndex() {
        if (submittedAnswer == null) {
            return -1;
        }
        try {
            return Integer.parseInt(submittedAnswer.strip()) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void endFeedback() {
        feedbackActive = false;
        runDeferred();
    }

    private void runDeferred() {
        List<Runnable> copy = new ArrayList<>(deferred);
        deferred.clear();
        for (Runnable r : copy) {
            r.run();
        }
    }

    /** 새 문제·새 전투 시작: 연출과 예약된 갱신을 모두 버리고 원래 모습으로 되돌린다. */
    private void cancelFeedback() {
        feedbackTimer.stop();
        feedbackActive = false;
        deferred.clear();
        stopFx();
        pendingPlayerLoss = 0;
        pendingMonsterLoss = 0;
    }

    /** 화면을 떠날 때: 연출 타이머를 멈추고 예약된 화면 갱신은 바로 마친다. */
    private void finishEffectsNow() {
        feedbackTimer.stop();
        feedbackActive = false;
        stopFx();
        runDeferred();
    }

    private void stopFx() {
        fxTimer.stop();
        anims.clear();
        floaters.clear();
        playerView.setFx(0, 0, 0);
        monsterView.setFx(0, 0, 0);
        fxLayer.repaint();
    }

    /** 정답이면 주인공이 공격하고 몬스터가 맞는다. 오답·시간 초과면 반대다. 숫자는 실제 HP가 줄어든 경우에만 띄운다. */
    private void playBattleFx(ResultType type) {
        long now = System.nanoTime();
        if (type == ResultType.CORRECT) {
            anims.add(new Anim(playerView, AnimType.LUNGE, 1, now, LUNGE_MS));
            anims.add(new Anim(monsterView, AnimType.SHAKE, 1, now, SHAKE_MS));
            if (pendingMonsterLoss > 0) {
                floaters.add(new Floater("-" + pendingMonsterLoss, DAMAGE_RED, false, now));
            }
        } else {
            anims.add(new Anim(monsterView, AnimType.LUNGE, -1, now, LUNGE_MS));
            anims.add(new Anim(playerView, AnimType.SHAKE, 1, now, SHAKE_MS));
            if (pendingPlayerLoss > 0) {
                floaters.add(new Floater("-" + pendingPlayerLoss, DAMAGE_RED, true, now));
            }
        }
        pendingPlayerLoss = 0;
        pendingMonsterLoss = 0;
        startFxTimer();
    }

    /** 회복에 성공했을 때: 주인공에게 초록 효과와 +n */
    private void playHeal(int amount) {
        long now = System.nanoTime();
        anims.add(new Anim(playerView, AnimType.HEAL, 0, now, HEAL_MS));
        floaters.add(new Floater("+" + amount, HEAL_GREEN, true, now));
        startFxTimer();
    }

    private void startFxTimer() {
        stepFx();
        if (!fxTimer.isRunning() && (!anims.isEmpty() || !floaters.isEmpty())) {
            fxTimer.start();
        }
    }

    private void stepFx() {
        long now = System.nanoTime();
        double s = scaleNow();
        anims.removeIf(a -> (now - a.start()) / 1_000_000.0 >= a.durMs());
        floaters.removeIf(f -> (now - f.start()) / 1_000_000.0 >= FLOAT_MS);
        for (CharacterView v : new CharacterView[] {playerView, monsterView}) {
            double dx = 0;
            double flash = 0;
            double glow = 0;
            for (Anim a : anims) {
                if (a.view() != v) {
                    continue;
                }
                double t = Math.max(0, (now - a.start()) / 1_000_000.0 / a.durMs());
                switch (a.type()) {
                    case LUNGE -> dx += a.dir() * LUNGE_PX * Math.sin(Math.PI * t) * s;
                    case SHAKE -> {
                        dx += SHAKE_PX * Math.sin(t * Math.PI * 6) * (1 - t) * s;
                        flash = Math.max(flash, 0.55 * (1 - t));
                    }
                    case HEAL -> glow = Math.max(glow, Math.sin(Math.PI * t));
                }
            }
            v.setFx(dx, flash, glow);
        }
        fxLayer.repaint();
        if (anims.isEmpty() && floaters.isEmpty()) {
            fxTimer.stop();
        }
    }

    // ---------------------------------------------------------------------
    // 키 입력
    // ---------------------------------------------------------------------

    private void setupKeyBindings() {
        // 객관식: 숫자 키 1~4(일반·키패드) = 그 번호 보기를 누른 것과 같다. 키를 누르고 있어도 한 번만 처리한다.
        for (int n = 1; n <= 4; n++) {
            int choiceNo = n;
            for (boolean numpad : new boolean[] {false, true}) {
                int code = (numpad ? KeyEvent.VK_NUMPAD0 : KeyEvent.VK_0) + n;
                String name = (numpad ? "choice_np_" : "choice_") + n;
                bindKey(KeyStroke.getKeyStroke(code, 0, false), name, () -> onNumberKey(choiceNo));
                bindKey(KeyStroke.getKeyStroke(code, 0, true), name + "_up", () -> numberHeld[choiceNo] = false);
            }
        }
        // ESC: 일시정지 열기 / 일시정지 중이면 계속하기 (X 버튼과 같은 기능)
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "pause_toggle", () -> {
            if (popup == PopupType.PAUSE) {
                choose(PopupChoice.PAUSE_RESUME);
            } else {
                requestPause();
            }
        });
        // Enter: 결과 화면에서 다음 문제 (단답형 제출은 입력창의 Enter가 한다)
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit_or_next", () -> {
            if (popup != null) {
                return; // 팝업 버튼은 클릭으로만 고른다
            }
            if (phase == Phase.SHOWING_RESULT && nextWanted) {
                requestNext();
            } else if (phase == Phase.ANSWERING && currentQuiz instanceof InputQuiz) {
                submitInput();
            }
        });
    }

    /**
     * 숫자 키를 눌렀을 때. 키를 누른 채로 있으면 운영체제가 같은 키 입력을 반복해서 보내므로, 뗄 때까지(또는 오래 지나
     * 확실히 새로 누를 때까지) 두 번째 입력부터는 무시한다. 그래서 다음 문제가 곧바로 자동 제출되지 않는다.
     */
    private void onNumberKey(int choiceNo) {
        if (!panel.isShowing()) {
            return; // 퀴즈 화면이 아닌 곳의 키 입력에는 반응하지 않는다
        }
        long now = System.nanoTime();
        boolean repeat = numberHeld[choiceNo] && now - numberHeldAt[choiceNo] < 1_500_000_000L;
        numberHeld[choiceNo] = true;
        numberHeldAt[choiceNo] = now;
        if (repeat) {
            return;
        }
        selectChoiceByKey(choiceNo);
    }

    private void selectChoiceByKey(int choiceNo) {
        if (inputField.isFocusOwner()) {
            return;
        }
        chooseOption(choiceNo - 1);
    }

    /** 숫자 키 입력 테스트용: 실제 키 이벤트와 같은 경로 */
    void pressNumberKeyForTest(int choiceNo) {
        onNumberKey(choiceNo);
    }

    void releaseNumberKeyForTest(int choiceNo) {
        numberHeld[choiceNo] = false;
    }

    private void bindKey(KeyStroke stroke, String name, Runnable action) {
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        panel.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------------
    // 타이머
    // ---------------------------------------------------------------------

    private void startCountdown() {
        pausedRemainingMillis = -1;
        if (timeLimitSeconds == NO_TIME_LIMIT) {
            countdown.stop();
            timerLabel.setText("TIME --");
            timerLabel.setForeground(INK);
            timeFraction = 1.0;
            placeFire();
            return;
        }
        deadlineNanos = System.nanoTime() + timeLimitSeconds * 1_000_000_000L;
        updateTimerDisplay(timeLimitSeconds * 1000L);
        countdown.restart();
    }

    private void stopCountdown() {
        countdown.stop();
        pausedRemainingMillis = -1;
    }

    /** 남은 시간을 기억하고 타이머를 멈춘다 (일시정지·팝업·화면 전환). 이미 멈춰 있으면 그대로. */
    private void pauseCountdown() {
        if (phase == Phase.ANSWERING && countdown.isRunning()) {
            long remaining = remainingMillis();
            countdown.stop();
            pausedRemainingMillis = remaining;
            updateTimerDisplay(remaining);
        }
    }

    /** 멈춘 시점의 남은 시간부터 다시 센다. */
    private void resumeCountdown() {
        if (phase == Phase.ANSWERING && pausedRemainingMillis >= 0 && timeLimitSeconds != NO_TIME_LIMIT) {
            deadlineNanos = System.nanoTime() + pausedRemainingMillis * 1_000_000L;
            pausedRemainingMillis = -1;
            countdown.start();
        }
    }

    private long remainingMillis() {
        if (phase != Phase.ANSWERING || timeLimitSeconds == NO_TIME_LIMIT) {
            return 0;
        }
        if (pausedRemainingMillis >= 0) {
            return pausedRemainingMillis;
        }
        return Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000L);
    }

    private void onTimerTick() {
        if (phase != Phase.ANSWERING) {
            countdown.stop();
            return;
        }
        long remaining = remainingMillis();
        updateTimerDisplay(remaining);
        if (remaining <= 0) {
            onTimeUp();
        }
    }

    /**
     * 제한시간이 끝났을 때: 단답형에 입력한 답이 있으면 그 답을 자동 제출해 정답·오답으로 판정받고,
     * 답이 없을 때(객관식은 보기를 누르면 바로 제출되므로 항상)만 시간 초과로 알린다.
     * 수동 제출과 같은 submit 경로를 쓰므로 판정은 한 번만 일어난다. 팝업(일시정지 등) 중에는 타이머가 멈춰 있어 여기 오지 않는다.
     */
    private void onTimeUp() {
        if (phase != Phase.ANSWERING || currentQuiz == null || popup != null) {
            return;
        }
        String pending = pendingAnswer();
        if (pending != null) {
            submit(pending);
            return;
        }
        Quiz expired = currentQuiz;
        lockForJudging();
        if (listener != null) {
            listener.onTimeExpired(expired);
        }
    }

    /** 지금 화면에 들어 있는 답. 단답형은 입력한 내용, 객관식은 없음(누르는 즉시 제출). 없으면 null */
    private String pendingAnswer() {
        if (currentQuiz instanceof InputQuiz) {
            String text = inputField.getText();
            return text == null || text.isBlank() ? null : text;
        }
        return null;
    }

    /** 남은 시간(초)을 "TIME n"으로 보이고, 불꽃을 남은 비율만큼 타이머 선 위에서 왼쪽으로 옮긴다. */
    private void updateTimerDisplay(long remainingMillis) {
        int seconds = (int) Math.ceil(remainingMillis / 1000.0);
        timerLabel.setText(timeLimitSeconds == NO_TIME_LIMIT ? "TIME --" : "TIME " + seconds);
        timerLabel.setForeground(timeLimitSeconds != NO_TIME_LIMIT && seconds <= WARNING_SECONDS ? WARNING : INK);
        timeFraction = timeLimitSeconds <= 0 ? 1.0
                : Math.max(0.0, Math.min(1.0, remainingMillis / (timeLimitSeconds * 1000.0)));
        timerLine.set(timeFraction, timeLimitSeconds != NO_TIME_LIMIT && seconds <= WARNING_SECONDS);
        placeFire();
    }

    /** 불꽃 위치: 남은 시간 100%면 선의 오른쪽 끝, 0%면 왼쪽 끝. */
    private void placeFire() {
        Spec spec = SketchLayout.center(LINE_X + timeFraction * LINE_W - FIRE_W / 2, FIRE_Y, FIRE_W, FIRE_H);
        layout.setSpec(fire, spec);
        if (stage.getWidth() > 0 && stage.getHeight() > 0) {
            fire.setBounds(layout.toScreen(spec, stage.getWidth(), stage.getHeight()));
        }
    }

    // ---------------------------------------------------------------------
    // 그림 그리기용 내부 클래스
    // ---------------------------------------------------------------------

    /** 종이색 바탕(선택적으로 배경 이미지). 모든 요소가 이 위에 얹힌다. */
    private final class Stage extends JPanel {
        private static final long serialVersionUID = 1L;

        Stage() {
            setOpaque(true);
            setBackground(PAPER);
            setName("stage");
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundFile == null) {
                return;
            }
            Optional<BufferedImage> img = imageLoader.get(backgroundFile);
            if (img.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            double scale = Math.max((double) getWidth() / img.get().getWidth(), (double) getHeight() / img.get().getHeight());
            int dw = (int) Math.ceil(img.get().getWidth() * scale);
            int dh = (int) Math.ceil(img.get().getHeight() * scale);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img.get(), (getWidth() - dw) / 2, (getHeight() - dh) / 2, dw, dh, null);
            g2.dispose();
        }
    }

    /**
     * 캐릭터 그림 칸. 그리는 방법은 {@link CharacterArt}가 정한다. 좌우에 pad만큼 여유가 있어 앞으로 나가거나 흔들려도 잘리지 않는다.
     * 연출 값: dx(가로 이동 px), flash(붉게 점멸 0~1), glow(초록 회복 효과 0~1).
     */
    private static final class CharacterView extends JComponent {
        private static final long serialVersionUID = 1L;
        private CharacterArt art;
        private int pad;
        private double dx;
        private double flash;
        private double glow;

        CharacterView(CharacterArt art) {
            this.art = art;
            setOpaque(false);
            setFocusable(false);
        }

        void setArt(CharacterArt art) {
            this.art = art;
            repaint();
        }

        void setPad(int pad) {
            this.pad = Math.max(0, pad);
            repaint();
        }

        void setFx(double dx, double flash, double glow) {
            this.dx = dx;
            this.flash = flash;
            this.glow = glow;
            repaint();
        }

        double getDx() {
            return dx;
        }

        double getFlash() {
            return flash;
        }

        double getGlow() {
            return glow;
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            int inner = Math.max(1, w - 2 * pad);
            if (flash <= 0.001 && glow <= 0.001) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.translate(Math.round(dx), 0);
                art.paint(g2, pad, 0, inner, h);
                g2.dispose();
                return;
            }
            // 색 효과: 그림이 있는 부분(투명하지 않은 곳)에만 색을 얹는다
            BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = buf.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            art.paint(bg, pad, 0, inner, h);
            float alpha = (float) Math.min(0.6, Math.max(flash, glow * 0.7));
            bg.setComposite(AlphaComposite.SrcAtop.derive(alpha));
            bg.setColor(flash > 0.001 ? new Color(235, 40, 40) : new Color(60, 195, 95));
            bg.fillRect(0, 0, w, h);
            bg.dispose();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.drawImage(buf, (int) Math.round(dx), 0, null);
            g2.dispose();
        }
    }

    /** 타이머 선: 전체는 연한 회색, 남은 시간만큼은 주황(5초 이하는 빨강)이다. 왼쪽 끝이 고정이고 오른쪽 끝이 줄어든다. */
    private static final class TimerBar extends JComponent {
        private static final long serialVersionUID = 1L;
        private double fraction;
        private boolean warning;

        TimerBar() {
            setOpaque(false);
            setFocusable(false);
        }

        void set(double fraction, boolean warning) {
            this.fraction = Math.max(0, Math.min(1, fraction));
            this.warning = warning;
            repaint();
        }

        double getFraction() {
            return fraction;
        }

        boolean isWarning() {
            return warning;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TRACK_GRAY);
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
            int rw = (int) Math.round(w * fraction);
            if (rw > 0) {
                g2.setColor(warning ? WARNING : TIMER_ORANGE);
                g2.fill(new RoundRectangle2D.Double(0, 0, Math.max(rw, h), h, h, h));
            }
            g2.dispose();
        }
    }

    /** 맨 위 투명 층: 캐릭터 옆에 올라가며 사라지는 피해량(-n)·회복량(+n). 마우스 입력은 아래로 통과한다. */
    private final class FxLayer extends JComponent {
        private static final long serialVersionUID = 1L;

        FxLayer() {
            setOpaque(false);
            setFocusable(false);
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (floaters.isEmpty() || getWidth() <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            double k = getWidth() / SketchLayout.DESIGN_W;
            long now = System.nanoTime();
            g2.setFont(GameFonts.title((float) (FLOAT_FONT * k)));
            FontMetrics fm = g2.getFontMetrics();
            for (Floater f : floaters) {
                double t = (now - f.start()) / 1_000_000.0 / FLOAT_MS;
                if (t < 0 || t > 1) {
                    continue;
                }
                double cx = (f.onPlayer() ? PLAYER_CENTER_X : MONSTER_CENTER_X) * k;
                double y = ((f.onPlayer() ? FLOAT_Y_PLAYER : FLOAT_Y_MONSTER) - FLOAT_RISE * t) * k;
                float alpha = (float) Math.max(0, Math.min(1, (1 - t) / 0.45));
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setColor(f.color());
                g2.drawString(f.text(), (int) Math.round(cx - fm.stringWidth(f.text()) / 2.0), (int) Math.round(y));
            }
            g2.dispose();
        }
    }

    /** 이미지가 없을 때 쓰는 임시 표시: 가는 선 상자와 이름 (캐릭터를 새로 그리지 않는다). */
    private static void paintPlaceholder(Graphics2D g2, int x, int y, int w, int h, String label) {
        g2.setColor(HINT_GRAY);
        g2.drawRect(x + 2, y + 2, Math.max(1, w - 5), Math.max(1, h - 5));
        g2.setFont(GameFonts.text(Font.PLAIN, Math.max(10f, w / 12f)));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2);
    }

    /** 단답형 입력창: 테두리 없이(틀은 input_frame.png가 그린다), 비어 있으면 "정답 입력" 안내를 흐리게 보인다. */
    private static final class AnswerField extends JTextField {
        private static final long serialVersionUID = 1L;
        private static final String HINT = ANSWER_HINT;
        private Font hintFont;

        void setHintFont(Font font) {
            this.hintFont = font;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && isEnabled()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(hintFont != null ? hintFont : getFont());
                g2.setColor(HINT_GRAY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(HINT, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    // ---------------------------------------------------------------------
    // 텍스트 도우미
    // ---------------------------------------------------------------------

    record QuestionParts(String prompt, String code) {
    }

    /**
     * 문제를 설명 문장과 코드로 나눈다.
     *
     * 코드처럼 보이는 첫 줄부터 마지막 줄까지를 코드 블록으로 보고 줄바꿈·들여쓰기를 그대로 둔다.
     * 코드 앞의 설명은 문제 문장이 되고, 코드 뒤에 오는 설명(예: "위 코드의 출력은?")은 문제 문장 끝에 붙인다.
     * 코드처럼 보이는 줄이 없으면 여러 줄이라도 전부 설명 문장으로 표시한다.
     */
    static QuestionParts splitQuestion(String question) {
        String normalized = question.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int firstCode = -1;
        int lastCode = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isCodeLine(lines[i])) {
                if (firstCode < 0) {
                    firstCode = i;
                }
                lastCode = i;
            }
        }
        if (firstCode < 0) {
            return new QuestionParts(joinProse(lines, 0, lines.length), "");
        }
        String before = joinProse(lines, 0, firstCode);
        String after = joinProse(lines, lastCode + 1, lines.length);
        String prompt = before.isEmpty() ? after : (after.isEmpty() ? before : before + "\n" + after);
        String code = String.join("\n", Arrays.copyOfRange(lines, firstCode, lastCode + 1)).stripTrailing();
        return new QuestionParts(prompt, code);
    }

    private static final Pattern CODE_START = Pattern.compile(
            "^(public|private|protected|static|final|abstract|class|interface|enum|import|package|return"
                    + "|new|try|else|case|default|break|continue|throw)\\b.*"
                    + "|^(if|for|while|switch|catch)\\s*\\(.*"
                    + "|^[A-Za-z_][\\w.<>\\[\\], ]*\\s+[A-Za-z_]\\w*\\s*(=[^=].*|;.*|\\(.*)"
                    + "|^[A-Za-z_][\\w.\\[\\]]*\\s*(=[^=]|\\+\\+|--|\\+=|-=).*"
                    + "|^[A-Za-z_][\\w]*(\\.[A-Za-z_]\\w*)+\\s*\\(.*");

    /** 코드 줄인지 추정한다. 한글 설명 문장은 따옴표 밖에 한글이 있으면 코드로 보지 않는다(주석·세미콜론 줄 제외). */
    static boolean isCodeLine(String line) {
        String t = line.strip();
        if (t.isEmpty()) {
            return false;
        }
        if (t.startsWith("//") || t.startsWith("/*") || t.startsWith("*/") || t.startsWith("@")) {
            return true;
        }
        t = stripTrailingComment(t); // 예: acc.withdraw(2000);  // 실패
        if (t.isEmpty()) {
            return true;
        }
        if (t.endsWith(";") || t.endsWith("{") || t.endsWith("}")) {
            return true;
        }
        boolean hangul = containsHangulOutsideQuotes(t);
        if (hangul) {
            return false;
        }
        if (CODE_START.matcher(t).matches()) {
            return true;
        }
        // 들여쓴 줄에 코드 기호가 있으면 코드 (예: 줄이 나뉜 메서드 인자)
        return Character.isWhitespace(line.charAt(0)) && t.matches(".*[(){}\\[\\];=].*");
    }

    /** 따옴표 밖의 // 주석을 떼어 낸다. */
    private static String stripTrailingComment(String s) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && (inString || inChar)) {
                i++;
                continue;
            }
            if (ch == '"' && !inChar) {
                inString = !inString;
            } else if (ch == '\'' && !inString) {
                inChar = !inChar;
            } else if (!inString && !inChar && ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                return s.substring(0, i).strip();
            }
        }
        return s;
    }

    private static boolean containsHangulOutsideQuotes(String s) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && (inString || inChar)) {
                i++;
                continue;
            }
            if (ch == '"' && !inChar) {
                inString = !inString;
            } else if (ch == '\'' && !inString) {
                inChar = !inChar;
            } else if (!inString && !inChar && ch >= '\uAC00' && ch <= '\uD7A3') {
                return true;
            }
        }
        return false;
    }

    /** 설명 줄들을 합친다. 앞뒤 빈 줄은 지우고 각 줄의 앞뒤 공백만 정리한다. */
    private static String joinProse(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            String t = lines[i].strip();
            if (sb.length() > 0 || !t.isEmpty()) {
                sb.append(t).append('\n');
            }
        }
        return sb.toString().strip();
    }

    /** 사용자가 제출한 답을 화면에 보일 글로 바꾼다 (객관식은 "번호. 보기", 단답형은 입력한 글). 제출한 답이 없으면 null */
    static String submittedAnswerText(Quiz quiz, String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        if (quiz instanceof ChoiceQuiz choiceQuiz) {
            try {
                int no = Integer.parseInt(answer.strip());
                if (no >= 1 && no <= choiceQuiz.getChoices().size()) {
                    return no + ". " + choiceQuiz.getChoices().get(no - 1);
                }
            } catch (NumberFormatException e) {
                // 번호가 아니면 그대로 보인다
            }
        }
        return answer.strip();
    }

    private static String correctAnswerText(Quiz quiz) {
        if (quiz instanceof ChoiceQuiz choiceQuiz) {
            int no = choiceQuiz.getAnswerNo();
            return no + ". " + choiceQuiz.getChoices().get(no - 1);
        }
        if (quiz instanceof InputQuiz inputQuiz) {
            return inputQuiz.getAnswer();
        }
        return "-";
    }
}
