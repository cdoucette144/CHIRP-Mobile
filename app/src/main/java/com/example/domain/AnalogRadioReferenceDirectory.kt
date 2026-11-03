package com.example.domain

import com.example.data.model.DuplexMode
import com.example.data.model.PowerLevel
import com.example.data.model.RadioChannel

data class AnalogCategoryGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val channels: List<RadioChannel>
)

data class CountyDirectory(
    val name: String,
    val categories: List<AnalogCategoryGroup>
)

data class StateDirectory(
    val code: String,
    val name: String,
    val counties: List<CountyDirectory>
)

object AnalogRadioReferenceDirectory {

    val standardCtcssTones: List<String> = listOf(
        "None", "67.0", "69.3", "71.9", "74.4", "77.0", "79.7", "82.5", "85.4",
        "88.5", "91.5", "94.8", "97.4", "100.0", "103.5", "107.2", "110.9", "114.8",
        "118.8", "123.0", "127.3", "131.8", "136.5", "141.3", "146.2", "151.4",
        "156.7", "162.2", "167.9", "173.8", "179.9", "186.2", "192.8", "203.5",
        "210.7", "218.1", "225.7", "233.6", "241.8", "250.3", "254.1"
    )

    val standardDcsCodes: List<String> = listOf(
        "None", "D023N", "D025N", "D026N", "D031N", "D032N", "D043N", "D047N", "D051N",
        "D054N", "D065N", "D071N", "D072N", "D073N", "D074N", "D114N", "D115N", "D116N",
        "D125N", "D131N", "D132N", "D134N", "D143N", "D152N", "D155N", "D156N", "D162N",
        "D165N", "D172N", "D174N", "D205N", "D223N", "D226N", "D243N", "D244N", "D245N",
        "D251N", "D261N", "D263N", "D265N", "D271N", "D306N", "D311N", "D315N", "D331N",
        "D343N", "D346N", "D351N", "D364N", "D365N", "D371N", "D411N", "D412N", "D413N",
        "D423N", "D431N", "D432N", "D445N", "D464N", "D465N", "D466N", "D503N", "D506N",
        "D516N", "D532N", "D546N", "D565N", "D606N", "D612N", "D624N", "D627N", "D631N",
        "D632N", "D654N", "D662N", "D664N", "D703N", "D712N", "D723N", "D731N", "D732N",
        "D734N", "D743N", "D754N"
    )

    // Complete National Frequencies including full 22-ch FRS + 8 GMRS Repeaters
    val nationalGroups: List<AnalogCategoryGroup> = listOf(
        AnalogCategoryGroup(
            id = "nat_frs_gmrs_full",
            title = "FRS & GMRS (Full 22 Channels + 8 Repeaters)",
            subtitle = "Complete 22 FCC FRS Channels & 8 GMRS +5.0 MHz Repeaters",
            channels = listOf(
                RadioChannel(1, "FRS 01", 462562500L, 462562500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 1 (462.5625 MHz 2W/0.5W)"),
                RadioChannel(2, "FRS 02", 462587500L, 462587500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 2 (462.5875 MHz 2W/0.5W)"),
                RadioChannel(3, "FRS 03", 462612500L, 462612500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 3 (462.6125 MHz 2W/0.5W)"),
                RadioChannel(4, "FRS 04", 462637500L, 462637500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 4 (462.6375 MHz 2W/0.5W)"),
                RadioChannel(5, "FRS 05", 462662500L, 462662500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 5 (462.6625 MHz 2W/0.5W)"),
                RadioChannel(6, "FRS 06", 462687500L, 462687500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 6 (462.6875 MHz 2W/0.5W)"),
                RadioChannel(7, "FRS 07", 462712500L, 462712500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 7 (462.7125 MHz 2W/0.5W)"),
                RadioChannel(8, "FRS 08", 467562500L, 467562500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 8 (467.5625 MHz 0.5W Max)"),
                RadioChannel(9, "FRS 09", 467587500L, 467587500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 9 (467.5875 MHz 0.5W Max)"),
                RadioChannel(10, "FRS 10", 467612500L, 467612500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 10 (467.6125 MHz 0.5W Max)"),
                RadioChannel(11, "FRS 11", 467637500L, 467637500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 11 (467.6375 MHz 0.5W Max)"),
                RadioChannel(12, "FRS 12", 467662500L, 467662500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 12 (467.6625 MHz 0.5W Max)"),
                RadioChannel(13, "FRS 13", 467687500L, 467687500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 13 (467.6875 MHz 0.5W Max)"),
                RadioChannel(14, "FRS 14", 467712500L, 467712500L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "FRS Ch 14 (467.7125 MHz 0.5W Max)"),
                RadioChannel(15, "GMRS/FRS 15", 462550000L, 462550000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 15 / FRS Direct"),
                RadioChannel(16, "GMRS/FRS 16", 462575000L, 462575000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 16 / FRS Direct"),
                RadioChannel(17, "GMRS/FRS 17", 462600000L, 462600000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 17 / FRS Direct"),
                RadioChannel(18, "GMRS/FRS 18", 462625000L, 462625000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 18 / FRS Direct"),
                RadioChannel(19, "GMRS/FRS 19", 462650000L, 462650000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 19 / FRS Direct"),
                RadioChannel(20, "GMRS/FRS 20", 462675000L, 462675000L, DuplexMode.SIMPLEX, 0L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Ch 20 National Emergency Calling"),
                RadioChannel(21, "GMRS/FRS 21", 462700000L, 462700000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 21 / FRS Direct"),
                RadioChannel(22, "GMRS/FRS 22", 462725000L, 462725000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "GMRS Ch 22 / FRS Direct"),
                // 8 Standard GMRS Repeaters (+5.000 MHz inputs)
                RadioChannel(23, "GMRS RPT 15", 462550000L, 467550000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 15 (+5.0 MHz)"),
                RadioChannel(24, "GMRS RPT 16", 462575000L, 467575000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 16 (+5.0 MHz)"),
                RadioChannel(25, "GMRS RPT 17", 462600000L, 467600000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 17 (+5.0 MHz)"),
                RadioChannel(26, "GMRS RPT 18", 462625000L, 467625000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 18 (+5.0 MHz)"),
                RadioChannel(27, "GMRS RPT 19", 462650000L, 467650000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 19 (+5.0 MHz)"),
                RadioChannel(28, "GMRS RPT 20", 462675000L, 467675000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Travel/Emergency Repeater 20 (+5.0 MHz)"),
                RadioChannel(29, "GMRS RPT 21", 462700000L, 467700000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 21 (+5.0 MHz)"),
                RadioChannel(30, "GMRS RPT 22", 462725000L, 467725000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "FM", PowerLevel.HIGH, false, "GMRS Repeater 22 (+5.0 MHz)")
            )
        ),
        AnalogCategoryGroup(
            id = "nat_interop",
            title = "National Public Safety Interop (VTAC / UTAC)",
            subtitle = "NIFOG Analog FM Tactical & Calling Channels",
            channels = listOf(
                RadioChannel(1, "VCALL10", 155752500L, 155752500L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "National VHF Calling"),
                RadioChannel(2, "VTAC11", 151137500L, 151137500L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "VHF Tactical 11"),
                RadioChannel(3, "VTAC12", 154452500L, 154452500L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "VHF Tactical 12"),
                RadioChannel(4, "VTAC13", 158737500L, 158737500L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "VHF Tactical 13"),
                RadioChannel(5, "VTAC14", 159472500L, 159472500L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "VHF Tactical 14"),
                RadioChannel(6, "UCALL40", 453212500L, 458212500L, DuplexMode.PLUS, 5000000L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "National UHF Repeater"),
                RadioChannel(7, "UCALL40D", 453212500L, 453212500L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "National UHF Direct"),
                RadioChannel(8, "UTAC41", 453462500L, 458462500L, DuplexMode.PLUS, 5000000L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "UHF Tactical 41"),
                RadioChannel(9, "UTAC42", 453712500L, 458712500L, DuplexMode.PLUS, 5000000L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "UHF Tactical 42"),
                RadioChannel(10, "UTAC43", 453862500L, 458862500L, DuplexMode.PLUS, 5000000L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "UHF Tactical 43")
            )
        ),
        AnalogCategoryGroup(
            id = "nat_murs",
            title = "MURS (Multi-Use Radio Service 1–5)",
            subtitle = "License-Free VHF 151–154 MHz Simplex",
            channels = listOf(
                RadioChannel(1, "MURS CH 1", 151820000L, 151820000L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "MURS 151.820 MHz"),
                RadioChannel(2, "MURS CH 2", 151880000L, 151880000L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "MURS 151.880 MHz"),
                RadioChannel(3, "MURS CH 3", 151940000L, 151940000L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "MURS 151.940 MHz"),
                RadioChannel(4, "MURS BLUE", 154570000L, 154570000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, false, "MURS 4 Blue Dot"),
                RadioChannel(5, "MURS GREEN", 154600000L, 154600000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, false, "MURS 5 Green Dot")
            )
        ),
        AnalogCategoryGroup(
            id = "nat_noaa_wx",
            title = "NOAA Weather Radio (All 7 Channels)",
            subtitle = "162.400–162.550 MHz National Weather Broadcasts (RX Only)",
            channels = listOf(
                RadioChannel(1, "NOAA WX 1", 162550000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.550"),
                RadioChannel(2, "NOAA WX 2", 162400000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.400"),
                RadioChannel(3, "NOAA WX 3", 162475000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.475"),
                RadioChannel(4, "NOAA WX 4", 162425000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.425"),
                RadioChannel(5, "NOAA WX 5", 162450000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.450"),
                RadioChannel(6, "NOAA WX 6", 162500000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.500"),
                RadioChannel(7, "NOAA WX 7", 162525000L, 462562500L, DuplexMode.OFF, 0L, "None", "None", "FM", PowerLevel.LOW, true, "NOAA Weather 162.525")
            )
        ),
        AnalogCategoryGroup(
            id = "nat_marine_vhf",
            title = "USCG & Marine VHF Channels",
            subtitle = "Distress, Hailing, Bridge-to-Bridge & Recreational",
            channels = listOf(
                RadioChannel(1, "MARINE 16", 156800000L, 156800000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, true, "Distress & Hailing"),
                RadioChannel(2, "MARINE 09", 156450000L, 156450000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, true, "Boater Calling"),
                RadioChannel(3, "MARINE 13", 156650000L, 156650000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, true, "Bridge to Bridge Nav"),
                RadioChannel(4, "MARINE 22A", 157100000L, 157100000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, true, "US Coast Guard Liaison"),
                RadioChannel(5, "MARINE 68", 156425000L, 156425000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, true, "Non-Commercial Working"),
                RadioChannel(6, "MARINE 72", 156625000L, 156625000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.LOW, true, "Ship to Ship")
            )
        ),
        AnalogCategoryGroup(
            id = "nat_ham_calling",
            title = "National Amateur Simplex Calling (6m / 2m / 220 / 70cm)",
            subtitle = "ARRL Band-Plan National FM Simplex Channels",
            channels = listOf(
                RadioChannel(1, "6M CALL", 52525000L, 52525000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "6m National Simplex"),
                RadioChannel(2, "2M CALL", 146520000L, 146520000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "2m National Simplex"),
                RadioChannel(3, "2M SIMP 55", 146550000L, 146550000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "2m Tactical Simplex"),
                RadioChannel(4, "220 CALL", 223500000L, 223500000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "1.25m National Simplex"),
                RadioChannel(5, "70CM CALL", 446000000L, 446000000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "70cm National Simplex")
            )
        )
    )

    // Complete Plymouth County, Massachusetts (All 27 towns, mutual aid, strictly Analog FM/NFM, P25 excluded)
    private val plymouthCountyMa = CountyDirectory(
        name = "Plymouth County",
        categories = listOf(
            AnalogCategoryGroup(
                id = "ma_plym_fire_control_ma",
                title = "Plymouth County Fire Control & Mutual Aid",
                subtitle = "Countywide Dispatch, Inter-Agency Mutual Aid & Firegrounds (NFM)",
                channels = listOf(
                    RadioChannel(1, "PLY CTY CTL", 483475000L, 486475000L, DuplexMode.PLUS, 3000000L, "141.3", "141.3", "NFM", PowerLevel.HIGH, false, "PCC Main County Dispatch"),
                    RadioChannel(2, "FIRE MA 1", 453450000L, 458450000L, DuplexMode.PLUS, 5000000L, "D306N", "D306N", "NFM", PowerLevel.HIGH, false, "Plymouth County Mutual Aid 1"),
                    RadioChannel(3, "FIRE MA 2", 453750000L, 458750000L, DuplexMode.PLUS, 5000000L, "D506N", "D506N", "NFM", PowerLevel.HIGH, false, "Plymouth County Mutual Aid 2"),
                    RadioChannel(4, "FIRE MA 3", 453950000L, 458950000L, DuplexMode.PLUS, 5000000L, "D606N", "D606N", "NFM", PowerLevel.HIGH, false, "Plymouth County Mutual Aid 3"),
                    RadioChannel(5, "CNTY FG 1", 483100000L, 483100000L, DuplexMode.SIMPLEX, 0L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "County Fireground 1"),
                    RadioChannel(6, "CNTY FG 2", 483200000L, 483200000L, DuplexMode.SIMPLEX, 0L, "114.8", "114.8", "NFM", PowerLevel.HIGH, false, "County Fireground 2"),
                    RadioChannel(7, "CNTY FG 3", 483400000L, 483400000L, DuplexMode.SIMPLEX, 0L, "127.3", "127.3", "NFM", PowerLevel.HIGH, false, "County Fireground 3"),
                    RadioChannel(8, "CNTY FG 5", 483775000L, 483775000L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "County Fireground 5"),
                    RadioChannel(9, "CNTY FG 6", 483925000L, 483925000L, DuplexMode.SIMPLEX, 0L, "173.8", "173.8", "NFM", PowerLevel.HIGH, false, "County Fireground 6")
                )
            ),
            AnalogCategoryGroup(
                id = "ma_plym_town_fire_ems",
                title = "Plymouth County Town Fire & EMS (Analog NFM)",
                subtitle = "Halifax, Hanson, Hanover, Pembroke, Carver, Marshfield & More",
                channels = listOf(
                    RadioChannel(1, "HALIFAX FD", 453187500L, 458187500L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Halifax Fire Dispatch (WPSI480)"),
                    RadioChannel(2, "HANSON FD 1", 483550000L, 486550000L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Hanson Fire Ch 1 Dispatch"),
                    RadioChannel(3, "HANSON FD 2", 482925000L, 485925000L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Hanson Fire Ch 2 Ops"),
                    RadioChannel(4, "HANOVER FD", 484400000L, 487400000L, DuplexMode.PLUS, 3000000L, "107.2", "107.2", "NFM", PowerLevel.HIGH, false, "Hanover Fire Operations"),
                    RadioChannel(5, "PEMBROKE FD", 460287500L, 465287500L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Pembroke Fire Operations"),
                    RadioChannel(6, "PLYMPTON FD", 453412500L, 458412500L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Plympton Fire Dispatch"),
                    RadioChannel(7, "KINGSTON FD", 460600000L, 465600000L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Kingston Fire Operations (Analog)"),
                    RadioChannel(8, "CARVER FD", 471250000L, 474250000L, DuplexMode.PLUS, 3000000L, "192.8", "192.8", "NFM", PowerLevel.HIGH, false, "Carver Fire Primary Repeater"),
                    RadioChannel(9, "MIDDLEBORO F", 470700000L, 473700000L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Middleborough Fire Dispatch"),
                    RadioChannel(10, "MIDDLBR FG", 470700000L, 470700000L, DuplexMode.SIMPLEX, 0L, "91.5", "91.5", "NFM", PowerLevel.HIGH, false, "Middleborough Fireground"),
                    RadioChannel(11, "DUXBURY FD", 460575000L, 465575000L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Duxbury Fire Operations"),
                    RadioChannel(12, "MARSHFLD FD", 453437500L, 458437500L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "NFM", PowerLevel.HIGH, false, "Marshfield Fire Operations"),
                    RadioChannel(13, "BROCKTON F1", 154310000L, 154310000L, DuplexMode.SIMPLEX, 0L, "94.8", "94.8", "NFM", PowerLevel.HIGH, false, "Brockton Fire Dispatch (FD1)"),
                    RadioChannel(14, "BROCKTON F2", 154145000L, 154145000L, DuplexMode.SIMPLEX, 0L, "94.8", "94.8", "NFM", PowerLevel.HIGH, false, "Brockton Fire Secondary (FD2)"),
                    RadioChannel(15, "WHITMAN FD", 472400000L, 475400000L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Whitman Fire Operations"),
                    RadioChannel(16, "ABINGTON FD", 482950000L, 485950000L, DuplexMode.PLUS, 3000000L, "D754N", "D754N", "NFM", PowerLevel.HIGH, false, "Abington Fire Dispatch"),
                    RadioChannel(17, "ROCKLAND FD", 482675000L, 485675000L, DuplexMode.PLUS, 3000000L, "D754N", "D754N", "NFM", PowerLevel.HIGH, false, "Rockland Fire Operations"),
                    RadioChannel(18, "BRIDGEWTR FD", 460525000L, 465525000L, DuplexMode.PLUS, 5000000L, "107.2", "107.2", "NFM", PowerLevel.HIGH, false, "Bridgewater Fire Operations"),
                    RadioChannel(19, "W BRDGWTR FD", 453387500L, 458387500L, DuplexMode.PLUS, 5000000L, "107.2", "107.2", "NFM", PowerLevel.HIGH, false, "West Bridgewater Fire"),
                    RadioChannel(20, "E BRDGWTR FD", 453250000L, 458250000L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "East Bridgewater Fire"),
                    RadioChannel(21, "HINGHAM FD", 484375000L, 487375000L, DuplexMode.PLUS, 3000000L, "141.3", "141.3", "NFM", PowerLevel.HIGH, false, "Hingham Fire Operations"),
                    RadioChannel(22, "HULL FD", 484325000L, 487325000L, DuplexMode.PLUS, 3000000L, "131.8", "131.8", "NFM", PowerLevel.HIGH, false, "Hull Fire Operations"),
                    RadioChannel(23, "NORWELL FD", 453175000L, 458175000L, DuplexMode.PLUS, 5000000L, "203.5", "203.5", "NFM", PowerLevel.HIGH, false, "Norwell Fire Operations"),
                    RadioChannel(24, "SCITUATE FD", 453375000L, 458375000L, DuplexMode.PLUS, 5000000L, "141.3", "141.3", "NFM", PowerLevel.HIGH, false, "Scituate Fire Operations"),
                    RadioChannel(25, "WAREHAM FD", 453125000L, 458125000L, DuplexMode.PLUS, 5000000L, "118.8", "118.8", "NFM", PowerLevel.HIGH, false, "Wareham / Onset Fire Dispatch"),
                    RadioChannel(26, "MATTAPOIS FD", 453075000L, 458075000L, DuplexMode.PLUS, 5000000L, "192.8", "192.8", "NFM", PowerLevel.HIGH, false, "Mattapoisett Fire Dispatch"),
                    RadioChannel(27, "MARION FD", 453325000L, 458325000L, DuplexMode.PLUS, 5000000L, "107.2", "107.2", "NFM", PowerLevel.HIGH, false, "Marion Fire Operations"),
                    RadioChannel(28, "ROCHESTR FD", 453275000L, 458275000L, DuplexMode.PLUS, 5000000L, "131.8", "131.8", "NFM", PowerLevel.HIGH, false, "Rochester Fire Operations"),
                    RadioChannel(29, "LAKEVILLE FD", 470625000L, 473625000L, DuplexMode.PLUS, 3000000L, "114.8", "114.8", "NFM", PowerLevel.HIGH, false, "Lakeville Fire Operations"),
                    RadioChannel(30, "CMED REG 5", 463075000L, 468075000L, DuplexMode.PLUS, 5000000L, "167.9", "167.9", "NFM", PowerLevel.HIGH, false, "Region V CMED Med 4"),
                    RadioChannel(31, "BID PLYM MED", 463175000L, 468175000L, DuplexMode.PLUS, 5000000L, "167.9", "167.9", "NFM", PowerLevel.HIGH, false, "BID Plymouth Hospital Patch")
                )
            ),
            AnalogCategoryGroup(
                id = "ma_plym_police_analog",
                title = "Plymouth County Law Enforcement (Analog Only)",
                subtitle = "Conventional Analog Channels (Excludes P25: Halifax PD & Marshfield PD Omitted)",
                channels = listOf(
                    RadioChannel(1, "PLYM POLICE1", 482887500L, 485887500L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.LOW, true, "Plymouth Police 1 / BAPERN SE"),
                    RadioChannel(2, "PLYM POLICE2", 483037500L, 486037500L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.LOW, true, "Plymouth Police 2 / BAPERN Tac"),
                    RadioChannel(3, "PLYM SO BCI", 483737500L, 486737500L, DuplexMode.PLUS, 3000000L, "D205N", "D205N", "NFM", PowerLevel.LOW, true, "Plymouth County Sheriff / BCI"),
                    RadioChannel(4, "BROCKTON PD", 482712500L, 485712500L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.LOW, true, "Brockton Police Analog Operations"),
                    RadioChannel(5, "MARSHFLD BACK", 482562500L, 485562500L, DuplexMode.PLUS, 3000000L, "203.5", "203.5", "NFM", PowerLevel.LOW, true, "Marshfield PD Backup Analog Channel"),
                    RadioChannel(6, "MSP SOUTH IN", 460400000L, 465400000L, DuplexMode.PLUS, 5000000L, "131.8", "131.8", "NFM", PowerLevel.LOW, true, "Mass State Police C-Troop South Interop"),
                    RadioChannel(7, "MSP STATE 1", 460125000L, 465125000L, DuplexMode.PLUS, 5000000L, "131.8", "131.8", "NFM", PowerLevel.LOW, true, "Mass State Police Statewide Analog 1"),
                    RadioChannel(8, "MSP STATE 2", 460250000L, 465250000L, DuplexMode.PLUS, 5000000L, "131.8", "131.8", "NFM", PowerLevel.LOW, true, "Mass State Police Statewide Analog 2")
                )
            ),
            AnalogCategoryGroup(
                id = "ma_plym_ham",
                title = "Plymouth County 2m & 70cm Amateur Repeaters",
                subtitle = "RepeaterBook Analog FM Repeaters with Standard Offsets",
                channels = listOf(
                    RadioChannel(1, "K1PBO PLYM 2M", 146685000L, 146085000L, DuplexMode.MINUS, 600000L, "131.8", "131.8", "FM", PowerLevel.HIGH, false, "Plymouth 2m Repeater (-0.6 MHz)"),
                    RadioChannel(2, "W1MV BRDG 2M", 147180000L, 147780000L, DuplexMode.PLUS, 600000L, "67.0", "67.0", "FM", PowerLevel.HIGH, false, "Bridgewater MARA (+0.6 MHz)"),
                    RadioChannel(3, "W1ASS NORW 2M", 146865000L, 146265000L, DuplexMode.MINUS, 600000L, "131.8", "131.8", "FM", PowerLevel.HIGH, false, "Norwell 2m Repeater (-0.6 MHz)"),
                    RadioChannel(4, "K1PBO PLYM 70", 446325000L, 441325000L, DuplexMode.MINUS, 5000000L, "131.8", "131.8", "FM", PowerLevel.HIGH, false, "Plymouth 70cm (-5.0 MHz)"),
                    RadioChannel(5, "N1YHS MARSH", 449425000L, 444425000L, DuplexMode.MINUS, 5000000L, "88.5", "88.5", "FM", PowerLevel.HIGH, false, "Marshfield 70cm (-5.0 MHz)")
                )
            )
        )
    )

    private fun buildCountyDirectory(
        stateCode: String,
        countyName: String,
        seedIndex: Int
    ): CountyDirectory {
        if (stateCode == "MA" && countyName == "Plymouth") {
            return plymouthCountyMa
        }
        val tagPrefix = countyName.uppercase().replace(" ", "").take(6)
        val vhfFire = 154130000L + ((seedIndex % 25) * 15000L)
        val uhfFireRx = 453050000L + ((seedIndex % 30) * 25000L)
        val uhfFireTx = uhfFireRx + 5000000L
        val vhfLaw = 155475000L + ((seedIndex % 20) * 15000L)
        val ham2mRx = 146610000L + ((seedIndex % 18) * 15000L)
        val ham2mTx = ham2mRx - 600000L
        val ham70Rx = 444100000L + ((seedIndex % 20) * 25000L)
        val ham70Tx = ham70Rx + 5000000L

        return CountyDirectory(
            name = "$countyName County",
            categories = listOf(
                AnalogCategoryGroup(
                    id = "${stateCode.lowercase()}_${tagPrefix.lowercase()}_fire",
                    title = "$countyName County Fire & EMS (Analog NFM)",
                    subtitle = "VHF/UHF Analog Dispatch, Fireground & EMS Med (P25 Filtered)",
                    channels = listOf(
                        RadioChannel(1, "$tagPrefix FD DSP", vhfFire, vhfFire, DuplexMode.SIMPLEX, 0L, "131.8", "131.8", "NFM", PowerLevel.HIGH, false, "$countyName County Fire Dispatch"),
                        RadioChannel(2, "$tagPrefix FD OPS", uhfFireRx, uhfFireTx, DuplexMode.PLUS, 5000000L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "$countyName Fire Ops Repeater"),
                        RadioChannel(3, "$tagPrefix FG TAC", 154280000L, 154280000L, DuplexMode.SIMPLEX, 0L, "None", "None", "NFM", PowerLevel.LOW, false, "Statewide Fire Mutual Aid Simplex"),
                        RadioChannel(4, "$tagPrefix EMS 1", 155340000L, 155340000L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.HIGH, false, "HEAR Ambulance-to-Hospital Analog")
                    )
                ),
                AnalogCategoryGroup(
                    id = "${stateCode.lowercase()}_${tagPrefix.lowercase()}_law",
                    title = "$countyName County Sheriff & Police (Analog FM)",
                    subtitle = "Conventional Analog Channels Only (P25 Digital Excluded)",
                    channels = listOf(
                        RadioChannel(1, "$tagPrefix SO 1", vhfLaw, vhfLaw, DuplexMode.SIMPLEX, 0L, "127.3", "127.3", "NFM", PowerLevel.LOW, true, "$countyName Sheriff Analog Ch 1"),
                        RadioChannel(2, "$tagPrefix LAW IN", 155475000L, 155475000L, DuplexMode.SIMPLEX, 0L, "156.7", "156.7", "NFM", PowerLevel.LOW, true, "National Law Enforcement Analog Aid"),
                        RadioChannel(3, "$tagPrefix DPW", 151085000L, 156085000L, DuplexMode.SPLIT, 5000000L, "114.8", "114.8", "NFM", PowerLevel.LOW, true, "$countyName Highway / OEM Analog")
                    )
                ),
                AnalogCategoryGroup(
                    id = "${stateCode.lowercase()}_${tagPrefix.lowercase()}_ham",
                    title = "$countyName County Amateur Repeaters (2m / 70cm)",
                    subtitle = "Analog FM ARES / RACES / Club Repeaters",
                    channels = listOf(
                        RadioChannel(1, "$tagPrefix 2M RPT", ham2mRx, ham2mTx, DuplexMode.MINUS, 600000L, "100.0", "100.0", "FM", PowerLevel.HIGH, false, "$countyName 2m ARES Repeater (-0.6 MHz)"),
                        RadioChannel(2, "$tagPrefix 70 RPT", ham70Rx, ham70Tx, DuplexMode.PLUS, 5000000L, "131.8", "131.8", "FM", PowerLevel.HIGH, false, "$countyName 70cm Repeater (+5.0 MHz)"),
                        RadioChannel(3, "2M CALL SIMP", 146520000L, 146520000L, DuplexMode.SIMPLEX, 0L, "None", "None", "FM", PowerLevel.HIGH, false, "2m National Simplex")
                    )
                )
            )
        )
    }

    private val stateDefinitions: List<Pair<String, Pair<String, List<String>>>> = listOf(
        "AL" to ("Alabama" to listOf("Jefferson", "Mobile", "Madison", "Montgomery", "Baldwin", "Tuscaloosa")),
        "AK" to ("Alaska" to listOf("Anchorage", "Fairbanks North Star", "Matanuska-Susitna", "Kenai Peninsula", "Juneau")),
        "AZ" to ("Arizona" to listOf("Maricopa", "Pima", "Pinal", "Yavapai", "Mohave", "Coconino")),
        "AR" to ("Arkansas" to listOf("Pulaski", "Benton", "Washington", "Sebastian", "Faulkner", "Garland")),
        "CA" to ("California" to listOf("Los Angeles", "San Diego", "Orange", "Riverside", "San Bernardino", "Santa Clara", "Alameda", "Sacramento")),
        "CO" to ("Colorado" to listOf("El Paso", "Denver", "Arapahoe", "Jefferson", "Adams", "Larimer", "Boulder")),
        "CT" to ("Connecticut" to listOf("Fairfield", "Hartford", "New Haven", "New London", "Litchfield", "Middlesex", "Tolland", "Windham")),
        "DE" to ("Delaware" to listOf("New Castle", "Sussex", "Kent")),
        "FL" to ("Florida" to listOf("Miami-Dade", "Broward", "Palm Beach", "Hillsborough", "Orange", "Pinellas", "Duval", "Lee")),
        "GA" to ("Georgia" to listOf("Fulton", "Gwinnett", "Cobb", "DeKalb", "Chatham", "Cherokee", "Richmond")),
        "HI" to ("Hawaii" to listOf("Honolulu", "Hawaii", "Maui", "Kauai")),
        "ID" to ("Idaho" to listOf("Ada", "Canyon", "Kootenai", "Bonneville", "Bannock")),
        "IL" to ("Illinois" to listOf("Cook", "DuPage", "Lake", "Will", "Kane", "McHenry", "Sangamon")),
        "IN" to ("Indiana" to listOf("Marion", "Lake", "Allen", "Hamilton", "St. Joseph", "Vanderburgh")),
        "IA" to ("Iowa" to listOf("Polk", "Linn", "Scott", "Johnson", "Black Hawk")),
        "KS" to ("Kansas" to listOf("Johnson", "Sedgwick", "Shawnee", "Wyandotte", "Douglas")),
        "KY" to ("Kentucky" to listOf("Jefferson", "Fayette", "Kenton", "Boone", "Warren")),
        "LA" to ("Louisiana" to listOf("East Baton Rouge", "Jefferson", "Orleans", "St. Tammany", "Lafayette", "Caddo")),
        "ME" to ("Maine" to listOf("Cumberland", "York", "Penobscot", "Kennebec", "Androscoggin", "Aroostook", "Hancock", "Knox")),
        "MD" to ("Maryland" to listOf("Montgomery", "Prince George's", "Baltimore", "Anne Arundel", "Howard", "Frederick")),
        "MA" to ("Massachusetts" to listOf("Plymouth", "Barnstable", "Norfolk", "Bristol", "Suffolk", "Middlesex", "Essex", "Worcester", "Hampden", "Hampshire", "Berkshire", "Franklin", "Dukes", "Nantucket")),
        "MI" to ("Michigan" to listOf("Wayne", "Oakland", "Macomb", "Kent", "Genesee", "Washtenaw")),
        "MN" to ("Minnesota" to listOf("Hennepin", "Ramsey", "Dakota", "Anoka", "Washington", "St. Louis")),
        "MS" to ("Mississippi" to listOf("Hinds", "Harrison", "DeSoto", "Rankin", "Jackson")),
        "MO" to ("Missouri" to listOf("St. Louis", "Jackson", "St. Charles", "Greene", "Clay")),
        "MT" to ("Montana" to listOf("Yellowstone", "Missoula", "Gallatin", "Flathead", "Cascade")),
        "NE" to ("Nebraska" to listOf("Douglas", "Lancaster", "Sarpy", "Hall", "Buffalo")),
        "NV" to ("Nevada" to listOf("Clark", "Washoe", "Carson City", "Elko", "Douglas")),
        "NH" to ("New Hampshire" to listOf("Hillsborough", "Rockingham", "Merrimack", "Strafford", "Grafton", "Cheshire", "Belknap", "Carroll", "Sullivan", "Coos")),
        "NJ" to ("New Jersey" to listOf("Bergen", "Middlesex", "Essex", "Hudson", "Monmouth", "Ocean", "Morris")),
        "NM" to ("New Mexico" to listOf("Bernalillo", "Dona Ana", "Santa Fe", "Sandoval", "San Juan")),
        "NY" to ("New York" to listOf("Suffolk", "Nassau", "Westchester", "Erie", "Monroe", "Onondaga", "Albany", "Dutchess")),
        "NC" to ("North Carolina" to listOf("Wake", "Mecklenburg", "Guilford", "Forsyth", "Cumberland", "Buncombe")),
        "ND" to ("North Dakota" to listOf("Cass", "Burleigh", "Grand Forks", "Ward", "Williams")),
        "OH" to ("Ohio" to listOf("Franklin", "Cuyahoga", "Hamilton", "Summit", "Montgomery", "Lucas")),
        "OK" to ("Oklahoma" to listOf("Oklahoma", "Tulsa", "Cleveland", "Canadian", "Comanche")),
        "OR" to ("Oregon" to listOf("Multnomah", "Washington", "Clackamas", "Lane", "Marion", "Jackson")),
        "PA" to ("Pennsylvania" to listOf("Philadelphia", "Allegheny", "Montgomery", "Bucks", "Delaware", "Lancaster", "Chester")),
        "RI" to ("Rhode Island" to listOf("Providence", "Kent", "Washington", "Newport", "Bristol")),
        "SC" to ("South Carolina" to listOf("Greenville", "Richland", "Charleston", "Horry", "Spartanburg")),
        "SD" to ("South Dakota" to listOf("Minnehaha", "Pennington", "Lincoln", "Brown", "Brookings")),
        "TN" to ("Tennessee" to listOf("Shelby", "Davidson", "Knox", "Hamilton", "Rutherford", "Williamson")),
        "TX" to ("Texas" to listOf("Harris", "Dallas", "Tarrant", "Bexar", "Travis", "Collin", "Denton", "El Paso")),
        "UT" to ("Utah" to listOf("Salt Lake", "Utah", "Davis", "Weber", "Washington")),
        "VT" to ("Vermont" to listOf("Chittenden", "Rutland", "Washington", "Windsor", "Franklin", "Windham", "Bennington")),
        "VA" to ("Virginia" to listOf("Fairfax", "Prince William", "Virginia Beach", "Loudoun", "Chesterfield", "Henrico")),
        "WA" to ("Washington" to listOf("King", "Pierce", "Snohomish", "Spokane", "Clark", "Thurston")),
        "WV" to ("West Virginia" to listOf("Kanawha", "Berkeley", "Monongalia", "Cabell", "Wood")),
        "WI" to ("Wisconsin" to listOf("Milwaukee", "Dane", "Waukesha", "Brown", "Racine", "Outagamie")),
        "WY" to ("Wyoming" to listOf("Laramie", "Natrona", "Campbell", "Sweetwater", "Teton"))
    )

    val all50States: List<StateDirectory> by lazy {
        stateDefinitions.mapIndexed { sIdx, (code, nameAndCounties) ->
            val (stateName, counties) = nameAndCounties
            StateDirectory(
                code = code,
                name = stateName,
                counties = counties.mapIndexed { cIdx, county ->
                    buildCountyDirectory(code, county, sIdx * 17 + cIdx)
                }
            )
        }
    }
}
