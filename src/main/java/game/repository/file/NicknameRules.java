package game.repository.file;

import java.util.regex.Pattern;

/** 닉네임 규칙(1~12자의 한글·영문·숫자). 세이브 폴더 이름으로 쓰므로 저장소들이 같은 규칙으로 검사한다. */
final class NicknameRules {

    private static final Pattern VALID = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{1,12}");

    private NicknameRules() {
    }

    static boolean isValid(String nickname) {
        return nickname != null && VALID.matcher(nickname).matches();
    }
}
