import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from mannequin import render_loop
from exercises import EXERCISES, sample
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "preview")
os.makedirs(OUT, exist_ok=True)

only = sys.argv[1:] or None
N = 4
CELL = 200
for ex in EXERCISES:
    if only and ex["id"] not in only:
        continue
    us = [i / N for i in range(N)]
    poses = [sample(ex, u) for u in us]
    ims = render_loop(poses, size=CELL)
    cols = 2
    rows = (N + cols - 1) // cols
    sheet = Image.new("RGB", (cols * CELL, rows * CELL), (11, 19, 35))
    for i, im in enumerate(ims):
        bg = Image.new("RGBA", im.size, (11, 19, 35, 255))
        bg.alpha_composite(im)
        sheet.paste(bg.convert("RGB"), ((i % cols) * CELL, (i // cols) * CELL))
    sheet.save(os.path.join(OUT, "ex_" + ex["id"] + ".png"))
    print(ex["id"])
