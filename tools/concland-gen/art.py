import sys; sys.path.insert(0, '.')
from PIL import Image, ImageDraw, ImageEnhance
from defs import B
A = '../../concland-assets/tiles/'
def load(p): return Image.open(A+p).convert('RGBA')

GROUND=(118,118,118,255); GRASS=(92,160,72,255); DARK=(40,40,40,255)
def canvas(w,h,col=GROUND):
    return Image.new('RGBA',(w*8,h*8),col)

def gas():   return load('power/gas.png')
def oil():
    im = load('power/oil.png'); px = im.load()
    # recolour to distinguish from gas: shift greens/whites toward dark oil-tank tones
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a = px[x,y]
            if g > r+20 and g > b:      # green equipment -> black/orange tanks
                px[x,y] = (min(255,g), int(g*0.55), 20, a)
            elif r>200 and g>200 and b>200:
                px[x,y] = (60,60,70,a)
    return im
def solar(): return load('power/solar.png')
def wind():
    fr = load('power/wind_animation.png')
    c = canvas(2,2,GRASS)
    for i,(x,y) in enumerate([(0,0),(8,0),(0,8),(8,8)]):
        f = fr.crop(((i%3)*8,0,(i%3)*8+8,8)); c.alpha_composite(f,(x,y))
    return c
def hospital(): return load('public/hospital.png')
def library():  return load('public/library.png')
def university(): return load('public/university.png')
def shrine():
    c = canvas(1,1,GRASS); c.alpha_composite(load('public/shrine.png')); return c

def school():
    c = canvas(3,3,GRASS); d = ImageDraw.Draw(c)
    d.rectangle([1,1,22,11], fill=(230,220,190,255), outline=DARK)   # building
    d.rectangle([1,1,22,3], fill=(170,60,50,255))                      # roof
    for x in range(3,21,3): d.rectangle([x,6,x+1,8], fill=(90,150,220,255))
    d.rectangle([10,8,13,11], fill=(120,80,40,255))                    # door
    d.rectangle([9,0,14,1], fill=(240,240,240,255)); d.point((11,0),fill=(40,40,40,255))  # clock
    d.rectangle([2,14,21,22], fill=(200,160,110,255), outline=(170,130,90,255))  # schoolyard
    d.line([4,18,19,18], fill=(255,255,255,255))
    return c
def lab():
    c = canvas(4,4,GROUND); d = ImageDraw.Draw(c)
    d.rectangle([2,2,20,20], fill=(210,215,225,255), outline=DARK)
    for y in range(5,19,4):
        for x in range(4,19,4): d.rectangle([x,y,x+1,y+1], fill=(80,170,210,255))
    d.ellipse([19,3,30,14], fill=(235,235,240,255), outline=DARK)      # observatory dome
    d.line([24,3,28,0], fill=DARK)
    d.rectangle([22,18,30,30], fill=(60,110,70,255))                    # greenhouse
    for x in range(23,30,2): d.line([x,19,x,29], fill=(150,220,160,255))
    d.rectangle([2,23,19,30], fill=(90,90,90,255))
    for x in range(4,19,4): d.line([x,24,x,29], fill=(230,230,230,255))
    return c
def space():
    c = canvas(4,4,(150,150,140,255)); d = ImageDraw.Draw(c)
    d.rectangle([1,1,30,30], outline=(230,200,60,255))
    d.rectangle([12,2,20,29], fill=(90,90,90,255))                     # gantry
    d.polygon([(15,3),(18,3),(19,8),(19,24),(14,24),(14,8)], fill=(245,245,245,255))
    d.polygon([(16,1),(17,1),(18,4),(15,4)], fill=(220,40,40,255))
    d.polygon([(12,24),(14,19),(14,26)], fill=(220,40,40,255)); d.polygon([(21,24),(19,19),(19,26)], fill=(220,40,40,255))
    d.rectangle([14,25,19,27], fill=(255,150,40,255))
    d.rectangle([2,22,9,29], fill=(200,205,215,255), outline=DARK)     # control building
    d.rectangle([23,3,29,9], fill=(200,205,215,255), outline=DARK)
    d.ellipse([24,15,29,20], fill=(240,240,240,255), outline=DARK)     # radar dish
    return c
def prison():
    c = canvas(3,3,(80,80,80,255)); d = ImageDraw.Draw(c)
    d.rectangle([0,0,23,23], outline=(200,200,200,255))
    d.rectangle([1,1,22,22], outline=(60,60,60,255))
    d.rectangle([4,4,19,13], fill=(150,140,130,255), outline=DARK)
    for x in range(6,18,3): d.line([x,6,x,11], fill=(30,30,30,255))
    d.rectangle([4,16,19,20], fill=(170,150,110,255))                  # yard
    for (x,y) in [(0,0),(21,0),(0,21),(21,21)]: d.rectangle([x,y,x+2,y+2], fill=(230,230,230,255))
    return c
def onsen():
    c = canvas(3,3,GRASS); d = ImageDraw.Draw(c)
    d.polygon([(1,8),(11,1),(21,8)], fill=(110,70,50,255))              # ryokan roof
    d.rectangle([3,8,19,12], fill=(230,215,180,255), outline=DARK)
    d.rectangle([9,10,12,12], fill=(170,40,40,255))
    d.ellipse([3,14,20,23], fill=(140,140,140,255))                     # rocks
    d.ellipse([5,15,18,22], fill=(110,200,220,255))                     # hot water
    for x in (8,11,14):
        d.line([x,13,x+1,15], fill=(255,255,255,255)); d.line([x+1,15,x,17], fill=(255,255,255,255))
    return c
def pachinko():
    c = canvas(3,3,GROUND); d = ImageDraw.Draw(c)
    d.rectangle([1,3,22,20], fill=(240,60,140,255), outline=(255,230,60,255))
    d.rectangle([2,4,21,8], fill=(255,230,60,255))
    for x in range(3,21,2): d.point((x,6), fill=(230,30,30,255))
    for y in range(10,19,3):
        for x in range(3,21,3): d.point((x,y), fill=(255,255,255,255))
    d.rectangle([9,15,14,20], fill=(40,40,60,255))
    d.rectangle([1,21,22,23], fill=(60,60,60,255))
    for x in range(2,22,4): d.rectangle([x,22,x+1,22], fill=(255,255,255,255))
    return c
def heliport():
    c = canvas(3,3,GROUND); d = ImageDraw.Draw(c)
    d.ellipse([2,2,21,21], fill=(70,70,70,255), outline=(255,220,40,255))
    d.rectangle([7,6,8,17], fill=(255,255,255,255)); d.rectangle([15,6,16,17], fill=(255,255,255,255))
    d.rectangle([7,11,16,12], fill=(255,255,255,255))
    for (x,y) in [(0,0),(22,0),(0,22),(22,22)]: d.point((x,y), fill=(255,60,60,255))
    return c
def farm():
    c = canvas(3,3,GRASS)
    c.alpha_composite(load('agricultural/barn.png'),(0,0))
    c.alpha_composite(load('agricultural/silo.png').crop((0,0,8,16)),(16,0))
    for i,(x,y) in enumerate([(0,16),(8,16),(16,16),(16,8)]):
        t = ['agricultural/field.png','agricultural/orchard.png','agricultural/field.png','agricultural/farm.png'][i]
        c.alpha_composite(load(t),(x,y))
    return c

ART = dict(GAS=gas,OIL=oil,SOLAR=solar,WIND=wind,SCHOOL=school,HOSPITAL=hospital,LIBRARY=library,
    UNIVERSITY=university,LAB=lab,SPACE=space,PRISON=prison,SHRINE=shrine,ONSEN=onsen,
    PACHINKO=pachinko,HELIPORT=heliport,FARM=farm)
ORIGIN = dict(GAS='ConcLand power/gas.png',OIL='ConcLand power/oil.png (recoloured)',SOLAR='ConcLand power/solar.png',
    WIND='ConcLand power/wind_animation.png (3 frames tiled)',HOSPITAL='ConcLand public/hospital.png',
    LIBRARY='ConcLand public/library.png',UNIVERSITY='ConcLand public/university.png',SHRINE='ConcLand public/shrine.png',
    FARM='ConcLand agricultural/barn,silo,field,orchard,farm.png',
    SCHOOL='new (ConcLand school.png is a placeholder X)',LAB='new (ConcLand laboratory.png is a placeholder X)',
    SPACE='new (ConcLand space.png is a placeholder X)',PRISON='new (ConcLand prison.png is a placeholder P)',
    ONSEN='new (ConcLand onsen.png is a placeholder X)',PACHINKO='new (ConcLand pachinko.png is a placeholder X)',
    HELIPORT='new (ConcLand heliport.png is a placeholder P)')
