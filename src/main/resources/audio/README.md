# 오디오 리소스 생성

게임은 첫 재생 때 WAV 파일이 없는 경우 원본 간단 음원을 자동으로 생성합니다. 외부 라이브러리나 네트워크가 필요하지 않습니다.

기본 저장 위치: `~/.code-escape/audio/`

생성 파일: 시작/게임/퀴즈 BGM, 버튼·정답·오답·공격·피격·승리·패배·상자 효과음.
기존 WAV 파일은 덮어쓰지 않으므로 직접 준비한 음원으로 교체할 수 있습니다.

수동 생성도 가능합니다:

```sh
make
java -cp out game.audio.AudioAssetGenerator /원하는/출력/경로
```

BGM 화면 전환은 `ScreenManager`가 처리하고, 음소거와 BGM/SFX 음량 조절은 `AudioManager`의 `setMuted`, `setBgmVolume`, `setSfxVolume` API를 사용합니다. 볼륨 입력 범위는 0.0~1.0입니다.

오디오 장치나 WAV 재생을 사용할 수 없는 환경에서는 게임은 계속 실행되고 소리만 생략됩니다.
