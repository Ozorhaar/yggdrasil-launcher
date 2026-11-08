package com.ozorhaar.yggdrasil.launcher.ui

/**
 * Hovamol, the Sayings of the High One, in Henry Adams Bellows' translation (The Poetic Edda,
 * 1923, public domain; text from Project Gutenberg ebook #73533). One verse a day rolls along
 * the Norns card. The half-line breaks are dropped and the lines joined with " / ".
 */
object Hovamol {
    private val verses: List<Pair<Int, String>> = listOf(
        2 to "Hail to the giver! a guest has come; / Where shall the stranger sit? / Swift shall he be who with swords shall try / The proof of his might to make.",
        3 to "Fire he needs who with frozen knees / Has come from the cold without; / Food and clothes must the farer have, / The man from the mountains come.",
        4 to "Water and towels and welcoming speech / Should he find who comes to the feast; / If renown he would get, and again be greeted, / Wisely and well must he act.",
        5 to "Wits must he have who wanders wide, / But all is easy at home; / At the witless man the wise shall wink / When among such men he sits.",
        7 to "The knowing guest who goes to the feast, / In silent attention sits; / With his ears he hears, with his eyes he watches, / Thus wary are wise men all.",
        8 to "Happy the one who wins for himself / Favor and praises fair; / Less safe by far is the wisdom found / That is hid in another’s heart.",
        9 to "Happy the man who has while he lives / Wisdom and praise as well, / For evil counsel a man full oft / Has from another’s heart.",
        10 to "A better burden may no man bear / For wanderings wide than wisdom; / It is better than wealth on unknown ways, / And in grief a refuge it gives.",
        11 to "A better burden may no man bear / For wanderings wide than wisdom; / Worse food for the journey he brings not afield / Than an over-drinking of ale.",
        12 to "Less good there lies than most believe / In ale for mortal men; / For the more he drinks the less does man / Of his mind the mastery hold.",
        15 to "The son of a king shall be silent and wise, / And bold in battle as well; / Bravely and gladly a man shall go, / Till the day of his death is come.",
        16 to "The sluggard believes he shall live forever, / If the fight he faces not; / But age shall not grant him the gift of peace, / Though spears may spare his life.",
        17 to "The fool is agape when he comes to the feast, / He stammers or else is still; / But soon if he gets a drink is it seen / What the mind of the man is like.",
        18 to "He alone is aware who has wandered wide, / And far abroad has fared, / How great a mind is guided by him / That wealth of wisdom has.",
        19 to "Shun not the mead, but drink in measure; / Speak to the point or be still; / For rudeness none shall rightly blame thee / If soon thy bed thou seekest.",
        21 to "The herds know well when home they shall fare, / And then from the grass they go; / But the foolish man his belly’s measure / Shall never know aright.",
        22 to "A paltry man and poor of mind / At all things ever mocks; / For never he knows, what he ought to know, / That he is not free from faults.",
        23 to "The witless man is awake all night, / Thinking of many things; / Care-worn he is when the morning comes, / And his woe is just as it was.",
        26 to "An ignorant man thinks that all he knows, / When he sits by himself in a corner; / But never what answer to make he knows, / When others with questions come.",
        28 to "Wise shall he seem who well can question, / And also answer well; / Nought is concealed that men may say / Among the sons of men.",
        29 to "Often he speaks who never is still / With words that win no faith; / The babbling tongue, if a bridle it find not, / Oft for itself sings ill.",
        34 to "Crooked and far is the road to a foe, / Though his house on the highway be; / But wide and straight is the way to a friend, / Though far away he fare.",
        35 to "Forth shall one go, nor stay as a guest / In a single spot forever; / Love becomes loathing if long one sits / By the hearth in another’s home.",
        36 to "Better a house, though a hut it be, / A man is master at home; / A pair of goats and a patched-up roof / Are better far than begging.",
        38 to "Away from his arms in the open field / A man should fare not a foot; / For never he knows when the need for a spear / Shall arise on the distant road.",
        39 to "If wealth a man has won for himself, / Let him never suffer in need; / Oft he saves for a foe what he plans for a friend, / For much goes worse than we wish.",
        41 to "Friends shall gladden each other with arms and garments, / As each for himself can see; / Gift-givers’ friendships are longest found, / If fair their fates may be.",
        42 to "To his friend a man a friend shall prove, / And gifts with gifts requite; / But men shall mocking with mockery answer, / And fraud with falsehood meet.",
        44 to "If a friend thou hast whom thou fully wilt trust, / And good from him wouldst get, / Thy thoughts with his mingle, and gifts shalt thou make, / And fare to find him oft.",
        47 to "Young was I once, and wandered alone, / And nought of the road I knew; / Rich did I feel when a comrade I found, / For man is man’s delight.",
        48 to "The lives of the brave and noble are best, / Sorrows they seldom feed; / But the coward fear of all things feels, / And not gladly the niggard gives.",
        51 to "Hotter than fire between false friends / Does friendship five days burn; / When the sixth day comes the fire cools, / And ended is all the love.",
        52 to "No great thing needs a man to give, / Oft little will purchase praise; / With half a loaf and a half-filled cup / A friend full fast I made.",
        53 to "A little sand has a little sea, / And small are the minds of men; / Though all men are not equal in wisdom, / Yet half-wise only are all.",
        54 to "A measure of wisdom each man shall have, / But never too much let him know; / The fairest lives do those men live / Whose wisdom wide has grown.",
        55 to "A measure of wisdom each man shall have, / But never too much let him know; / For the wise man’s heart is seldom happy, / If wisdom too great he has won.",
        56 to "A measure of wisdom each man shall have, / But never too much let him know; / Let no man the fate before him see, / For so is he freest from sorrow.",
        57 to "A brand from a brand is kindled and burned, / And fire from fire begotten; / And man by his speech is known to men, / And the stupid by their stillness.",
        58 to "He must early go forth who fain the blood / Or the goods of another would get; / The wolf that lies idle shall win little meat, / Or the sleeping man success.",
        59 to "He must early go forth whose workers are few, / Himself his work to seek; / Much remains undone for the morning-sleeper, / For the swift is wealth half won.",
        62 to "When the eagle comes to the ancient sea, / He snaps and hangs his head; / So is a man in the midst of a throng, / Who few to speak for him finds.",
        64 to "The man who is prudent a measured use / Of the might he has will make; / He finds when among the brave he fares / That the boldest he may not be.",
        66 to "Too early to many a meeting I came, / And some too late have I sought; / The beer was all drunk, or not yet brewed; / Little the loathed man finds.",
        68 to "Fire for men is the fairest gift, / And power to see the sun; / Health as well, if a man may have it, / And a life not stained with sin.",
        69 to "All wretched is no man, though never so sick; / Some from their sons have joy, / Some win it from kinsmen, and some from their wealth, / And some from worthy works.",
        70 to "It is better to live than to lie a corpse, / The live man catches the cow; / I saw flames rise for the rich man’s pyre, / And before his door he lay dead.",
        71 to "The lame rides a horse, the handless is herdsman, / The deaf in battle is bold; / The blind man is better than one that is burned, / No good can come of a corpse.",
        72 to "A son is better, though late he be born, / And his father to death have fared; / Memory-stones seldom stand by the road / Save when kinsman honors his kin.",
        77 to "Cattle die, and kinsmen die, / And so one dies one’s self; / But a noble name will never die, / If good renown one gets.",
        78 to "Cattle die, and kinsmen die, / And so one dies one’s self; / One thing I know that never dies, / The fame of a dead man’s deeds.",
        82 to "When the gale blows hew wood, in fair winds seek the water; / Sport with maidens at dusk, for day’s eyes are many; / From the ship seek swiftness, from the shield protection, / Cuts from the sword, from the maiden kisses.",
        89 to "Hope not too surely for early harvest, / Nor trust too soon in thy son; / The field needs good weather, the son needs wisdom, / And oft is either denied.",
        95 to "The head alone knows what dwells near the heart, / A man knows his mind alone; / No sickness is worse to one who is wise / Than to lack the longed-for joy.",
    )

    /** Today's verse: the same all day, a different one each day, cycling through them all. */
    fun today(now: Long = System.currentTimeMillis()): Pair<Int, String> {
        val tz = java.util.TimeZone.getDefault()
        val day = Math.floorDiv(now + tz.getOffset(now), 86_400_000L)
        return verses[Math.floorMod(day, verses.size.toLong()).toInt()]
    }
}
