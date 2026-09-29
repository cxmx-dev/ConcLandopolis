# name, tool enum, label, w, h, cost, power, civic, police, comPop, indPop, needsPower, copter, pollution, desc
B = [
 ("GAS",        "CL_GAS",        "Gas Power Plant",     4,4, 4000, 1000,   0,   0,0,0, True, False, 50),
 ("OIL",        "CL_OIL",        "Oil Power Plant",     4,4, 3500,  900,   0,   0,0,0, True, False, 75),
 ("SOLAR",      "CL_SOLAR",      "Solar Farm",          2,2, 1500,  150,   0,   0,0,0, True, False, 0),
 ("WIND",       "CL_WIND",       "Wind Farm",           2,2, 1000,  100,   0,   0,0,0, True, False, 0),
 ("SCHOOL",     "CL_SCHOOL",     "School",              3,3, 1000,    0, 100,   0,0,0, True, False, 0),
 ("HOSPITAL",   "CL_HOSPITAL",   "General Hospital",    3,3, 1500,    0, 100,   0,0,0, True, False, 0),
 ("LIBRARY",    "CL_LIBRARY",    "Library",             3,3,  800,    0,  80,   0,0,0, True, False, 0),
 ("UNIVERSITY", "CL_UNIVERSITY", "University",          3,3, 3000,    0, 160,   0,0,0, True, False, 0),
 ("LAB",        "CL_LAB",        "Research Laboratory", 4,4, 4000,    0, 120,   0,0,1, True, False, 0),
 ("SPACE",      "CL_SPACE",      "Space Center",        4,4,15000,    0, 250,   0,2,2, True, False, 0),
 ("PRISON",     "CL_PRISON",     "Prison",              3,3, 3000,    0, -80, 800,0,0, True, False, 0),
 ("SHRINE",     "CL_SHRINE",     "Shrine",              1,1,  300,    0,  30,   0,0,0, False,False, 0),
 ("ONSEN",      "CL_ONSEN",      "Onsen (Hot Spring)",  3,3, 2500,    0, 140,   0,2,0, True, False, 0),
 ("PACHINKO",   "CL_PACHINKO",   "Pachinko Parlor",     3,3, 2000,    0, -40,   0,4,0, True, False, 0),
 ("HELIPORT",   "CL_HELIPORT",   "Heliport",            3,3, 2500,    0,   0,   0,0,0, True, True,  25),
 ("FARM",       "CL_FARM",       "Farm",                3,3,  300,    0,   0,   0,0,2, False,False, 0),
]
FIRST_TILE = 1024
FIRST_DESC = 33
