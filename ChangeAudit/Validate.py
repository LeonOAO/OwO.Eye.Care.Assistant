"""Static source checks and Python reference model; NOT Kotlin/JUnit/device execution."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
base = root / "app/src/main/java/owo/eye/care/assistant"
gate = (base / "distance/DistanceGate.kt").read_text(encoding="utf-8")
camera = (base / "service/DistanceForegroundService.kt").read_text(encoding="utf-8")
ui = (base / "ui/CalibrationActivity.kt").read_text(encoding="utf-8")
assert "cm < 25" in gate and "cm >= 30" in gate
assert "coerceAtMost(2)" in gate
assert "setTargetResolution(Size(480, 360))" in camera
assert "frameCounter.incrementAndGet() % 2 != 0" in camera
assert "session == generation && usageMonitor.isActive()" in camera
assert "CalibrationBridge.surface" in camera
assert "CalibrationBridge.detach()" in ui
assert "getProfile(profile)" in camera
assert "setRefFaceWidthPxAt30cm" not in camera
assert "cameraProvider?.unbind(it)" in camera
assert "testDebugUnitTest assembleDebug" in (root / ".github/workflows/android-build.yml").read_text()
for file in (root / "app/src/main").rglob("*.xml"):
    ET.parse(file)
print("PASS: XML parse and source invariants")
# 檢查非字串／註解區的括號配對；這不是 Kotlin 語法或型別檢查。
pattern = '/\\*.*?\\*/|//[^\\n]*|"(?:\\\\.|[^"\\\\])*"|\\x27(?:\\\\.|[^\\x27\\\\])*\\x27'
for file in (root / "app/src").rglob("*.kt"):
    text = re.sub(pattern, "", file.read_text(encoding="utf-8"), flags=re.S)
    stack = []
    for char in text:
        if char in "({[": stack.append(char)
        elif char in ")}]":
            assert stack and stack.pop() == {")": "(", "}": "{", "]": "["}[char], file
    assert not stack, file
print("PASS: Kotlin delimiter balance (not compilation)")
class GateModel:
    def __init__(self): self.reset()
    def reset(self): self.show = False; self.near = self.far = 0
    def update(self, cm):
        if cm <= 0: self.reset()
        elif cm < 25:
            self.far = 0; self.near = min(2, self.near + 1)
            if self.near == 2: self.show = True
        elif cm >= 30:
            self.near = 0; self.far = min(2, self.far + 1)
            if self.far == 2: self.show = False
        else: self.near = self.far = 0
        return self.show
cases = [
    ([24,24,30,30], [False,True,True,False]),
    ([24,25,24,24], [False,False,False,True]),
    ([24,24,30,29,30,30], [False,True,True,True,True,False]),
    ([24,24,0,24], [False,True,False,False]),
    ([25,25,29,29], [False,False,False,False]),
]
for inputs, expected in cases:
    model = GateModel()
    assert [model.update(cm) for cm in inputs] == expected
    print(f"PASS MODEL: {inputs} -> {expected}")
assert (sorted(range(200,210))[4] + sorted(range(200,210))[5]) // 2 == 204
print("PASS MODEL: ten-value median = 204")
print("NOT RUN: Kotlin compilation, JUnit, Android camera/lifecycle tests, FPS/thermal tests")
