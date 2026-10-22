import math, struct, wave
from pathlib import Path
# Original synthesized game cues. Run manually; no network or build-time generator.
root=Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
root.mkdir(parents=True, exist_ok=True)
rate=22050
def emit(name, duration, notes):
 frames=[]
 for i in range(int(rate*duration)):
  t=i/rate
  sample=0
  for start,dur,hz,amp in notes:
   u=t-start
   if 0<=u<dur:
    envelope=min(1,u/.004)*math.exp(-u*9/dur)*(1-u/dur)
    sample+=amp*envelope*(math.sin(2*math.pi*hz*u)+.18*math.sin(2*math.pi*hz*2*u))
  frames.append(struct.pack('<h',int(max(-1,min(1,sample)) * 26000)))
 with wave.open(str(root/(name+'.wav')),'wb') as out:
  out.setnchannels(1);out.setsampwidth(2);out.setframerate(rate);out.writeframes(b''.join(frames))
emit('game_tap',.08,[(0,.07,740,.5)])
emit('game_tick',.09,[(0,.08,1100,.35)])
emit('game_start',.32,[(0,.16,523,.45),(.08,.18,659,.45),(.16,.16,784,.5)])
emit('game_reveal',.48,[(0,.25,659,.5),(.1,.25,831,.45),(.2,.28,988,.4)])
emit('game_complete',.75,[(0,.35,523,.5),(.12,.35,659,.5),(.24,.35,784,.5),(.38,.36,1047,.5)])
