var input = document.getElementById("rangeElement");
var inputvalue;
var urlTypeToggle;
var volumeInput = document.getElementById("volumeEdit");
var sendSaveVideoInput = document.getElementById("sendVideoInput");;

//var spacer = "";
var sendURL = 0;
var sendValuee = 1;
var stopStart = 2;
var sendVolumee = 3;
var sendLoopp = 4;
var sendVideoResolutionn = 5;
var sendUrlPlayerr = 6;
var sendLanguagee = 7;
var sendHardwaree = 8;
var sendSourceType = 9;
var sendPlaylistt = 10;
var sendSaveVideoo = 11;
var sendDeleteSavedVideoo = 12;
var sendSendSavedVideoo = 13;
var sendSendLoopPlaylistt = 14;
var sendSendColorFormatt = 15;
var sendSendFromSource = 16;
var sendSendLanguagesOfStream = 17;
var sendSendLanguagesRefresh = 18;
var sendLivePlayerr = 19;
var sendYoutubePlayerr = 20;
var sendYoutubePlayerrVideo = 21;
var sendYoutubeCachingg = 22;
var sendURLCachingg = 23;
var sendVideoResolutionLivee = 24;
var sendYoutubeLegacyPlayerrOnn = 25;
var sendCheckMacAndSsidd = 26;
var sendRadioPlayerr = 27;

const volumeMax = 100;
var nameOfMedia = "";
var volumePosition = 0;
var seekMax = 0;
var seekPosion = 0;
var timer = false;
var setUpOn = false;
var loopOn = false;
var hardwareOn = false;
var youtubeCachingOn = false;
var urlCachingOn = false;
var youtubeLegacyPlayerOn = false;
var checkMacAndSsidOn = false;
var sourceType = 0;
var secondSourceTypeIndex = 0;
let sourceTypes = [];
var playlistOn = false;
var loopPlaylist = false;
var saveLink = false;
var isLive = false;
var allLanguages = [];

var timerStoped = true;
var timerState = false;
let buttonsList = [];
let inputList = [];

function DisableAll(c)
{
    var b = document.getElementsByTagName("button");
    var f = document.getElementsByTagName("input");

    for (var a = 0; a < b.length; a++) {
        b[a].disabled = c;
    }
    for (var aa = 0; aa < f.length; aa++) {
        f[aa].disabled = c;
    }
}

function DisableButtonsByClass(on,text)
{
    var b = document.getElementsByClassName(text);

    for (var a = 0; a < b.length; a++) {
        b[a].disabled = on;
    }
}

function HideElementsByClass(on,text)
{
    var b = document.getElementsByClassName(text);

    for (var a = 0; a < b.length; a++) {
        b[a].hidden = on;
    }
}


function Timer()
{

if(timer)
{
    document.getElementById("startStopSetUp").innerHTML = "<i class='bi bi-pause-fill'></i>";

    if(seekMax==0){
        inputvalue.innerHTML = "Live";
    }
    else{
        sliderLoopStarter();
    }
}
else
{
    document.getElementById("startStopSetUp").innerHTML = "<i class='bi bi-play-fill'></i>";
}

}


function LoopPlaylistUp()
{
ButtonSetup("loopPlaylist",loopPlaylist);
}

function LoopUp()
{
ButtonSetup("loop",loopOn);
}


function HardwareUp()
{
    ButtonSetupV2("hardware",hardwareOn);
}
function YouTubeCachingUp()
{
    ButtonSetupV2("youtubeCaching",youtubeCachingOn);
}
function URLCachingUp()
{
    ButtonSetupV2("urlCaching",urlCachingOn);
}
function YoutubeLegacyPlayerOnUP()
{
    ButtonSetupV2("ytLegacyPlayer",youtubeLegacyPlayerOn);
}
function CheckMacAndSsidOnUP()
{
    ButtonSetupV2("ckMacAndSsid",checkMacAndSsidOn);
}



function ButtonSetup(idKey,on)
{

if(on)
{
    document.getElementById(idKey).classList.remove('btn-primary');
    document.getElementById(idKey).classList.add('btn-success');
}
else
{
    document.getElementById(idKey).classList.remove('btn-success');
    document.getElementById(idKey).classList.add('btn-primary');
}
}

function ButtonSetupV2(idKey,on)
{
var e = document.getElementById(idKey);
e.disabled = false;
if(on)
{
    e.checked = true;
}
else
{
    e.checked = false;
}
}

function dataGet(data)
{
    if(data!=0)
    return true;
    else
    return false;
}

async function loadData()
{
    const response = await fetchUniversal("Data");
    const obj = JSON.parse(response);
    const data = obj[0];
    const types = obj[1];
    allLanguages = obj[2];

    //volumeMax = data[0];
    
    sourceTypes = types;


    volumePosition = data[0];
    seekMax = data[1];
    seekPosion = data[2];

    input.max = seekMax;
    input.value = seekPosion;
    volumeInput.max = volumeMax;
    volumeInput.value = volumePosition;

    timer = dataGet(data[3]);

    setUpOn = dataGet(data[4]);

    loopOn = dataGet(data[5]);

    sourceType = data[6];


    playlistOn = dataGet(data[7]);

    loopPlaylist = dataGet(data[8]);

    saveLink = dataGet(data[9]);

    isLive = dataGet(data[10]);

    nameOfMedia = data[11];

/*    
if(setUpOn)
{
    document.getElementById('inputbox').remove();
    wait(true);
    var waitt=0;
    function waitbeforestart(a)
    {
        setTimeout(function()
        {
            if(waitt<a)
            {
                waitt++;waitbeforestart()
            }
            else
            {
                wait(false)
            }
        },100)
    }
    waitbeforestart(1);
}
else
{

    document.getElementById('outputbox').remove();
    wait(true);
    document.getElementById('youtubeSend').disabled = false;
}
*/
}

async function SetUp()
{
    await loadData();

    if(setUpOn)
    {
        
        if(nameOfMedia != null && nameOfMedia != ""){
            document.getElementById('nameOfMedia').textContent = nameOfMedia;
            document.getElementById('nameOfMediaDiv').hidden = false;
        }

        DisableAll(false);
    
        inputvalue = document.getElementById("outputpanel");
        document.getElementById("outputbox").hidden = false;
        HideElementsByClass(true,'inputbox');
        setSliderValue();
        HideElementsByClass(!saveLink,'saveButton');
        HideElementsByClass(isLive,'liveornot');
        HideElementsByClass(!playlistOn,'playlistButtons');
    }
    else
    {

        document.getElementById('nameOfMediaDiv').hidden = true;

        urlTypeToggle = document.getElementById('urlTypeToggle');
        HideElementsByClass(false,'inputbox');
        urlTypeToggle.disabled = false;
        DisableButtonsByClass(false,"videoSettingsButton");
        inputvalue = document.getElementById("inputpanel");
        urlTypeLoad();
        document.getElementById("outputbox").hidden = true;
        inputvalue.disabled = false;
        volumeInput.disabled = false;
        document.getElementById('youtubeSend').disabled = false;
        HideElementsByClass(true,'playlistButtons');

    }

    DisableButtonsByClass(false,"Devices");

    Timer();
    LoopUp();
    LoopPlaylistUp();
}

async function PageOpen()
{
    DisableAll(true);
    await SetUp();
    document.body.hidden = false;

}

async function SendRequest(value)
{
    //stoper();
    const json = value;

    await fetch("Data",
    {
    method: "POST",
    body: JSON.stringify(json),
    headers: {
    "Content-type": "application/json; charset=UTF-8"
    }
    });
            
    //window.location.href = window.location.href;
}


async function ServerOk() {
    try {
        // Make the HTTP GET request
        const response = await fetch("Ok");
        
        // Check if the response status is 200 (OK)
        if (!response.ok) {
            console.error('Server returned an error status:', response.status);
        }
    } catch (error) {
        console.error('Request failed:', error);
    }
}



/*
async function ServerOk() {
    try {
        // Make the HTTP GET request
        const response = await fetch("Ok");
        
        // Check if the response status is 200 (OK)
        if (!response.ok) {
            console.error('Server returned an error status:', response.status);
        }
    } catch (error) {
        console.error('Request failed:', error);
    }
}
*/

function SendRequestBool(on,page,value)
{
    if(on)
    {
        return SendRequestOn(page,value);
    }
    else
    {
        return SendRequestOff(page);
    }
}
function SendRequestOn(page,value)
{
    return [page,value];
}
function SendRequestOff(page)
{
    return [page]
}
    
async function sendLink()
{
    DisableAll(true);

    await SendRequest(SendRequestBool(!setUpOn,sendURL,inputvalue.value));

    await ServerOk();

    // Wait for 500ms after the response is received
    await new Promise(resolve => setTimeout(resolve, 500));

    await SetUp();

    //alert(volumePosition +" "+ seekMax +" "+ seekPosion +" "+ timerOn +" "+ setUpOn +" "+ loopOn);
}
async function startStop()
{
    WaitOn();

    seekPosion = input.value;
    await SendRequest(SendRequestBool(timerState,stopStart,seekPosion));
    timerState = !timerState;

    await WaitOff();
}
async function sendValue()
{
    WaitOn();

    seekPosion = input.value;
    await SendRequest(SendRequestOn(sendValuee,seekPosion));

    await WaitOff();
}
async function sendVolume()
{
    WaitOn();

    volumePosition = volumeInput.value;
    seekPosion = input.value;
    await SendRequest(SendRequestOn(sendVolumee,volumePosition));

    await WaitOff();
}
async function sendPlaylist(change)
{
    WaitOn();

    await SendRequest(SendRequestOn(sendPlaylistt,change));

    await WaitOff();

    //await ServerOk();

    await SetUp();
}

async function sendVideoBase(functionName,functioId,valueId)
{
    WaitOn();

    var saved = document.getElementById(functionName+"Saved");
    saved.textContent = document.getElementById(functionName+"Sel"+valueId).textContent;

    await SendRequest(SendRequestOn(functioId,valueId));

    await WaitOff();
}

async function sendVideoResolution(valueId)
{
    sendVideoBase(sendVideoResolution.name,sendVideoResolutionn,valueId);
}

async function sendVideoResolutionLive(valueId)
{
    sendVideoBase(sendVideoResolutionLive.name,sendVideoResolutionLivee,valueId);
}

async function sendUrlPlayer(valueId)
{
    sendVideoBase(sendUrlPlayer.name,sendUrlPlayerr,valueId);
}

async function sendYoutubePlayer(valueId)
{
    sendVideoBase(sendYoutubePlayer.name,sendYoutubePlayerr,valueId);
}

async function sendYoutubePlayerVideo(valueId)
{
    sendVideoBase(sendYoutubePlayerVideo.name,sendYoutubePlayerrVideo,valueId);
}

async function sendLivePlayer(valueId)
{
    sendVideoBase(sendLivePlayer.name,sendLivePlayerr,valueId);
}

async function sendRadioPlayer(valueId)
{
    sendVideoBase(sendRadioPlayer.name,sendRadioPlayerr,valueId);
}

async function sendColorFormat(valueId)
{
    sendVideoBase(sendColorFormat.name,sendSendColorFormatt,valueId);
}

async function setList(functionName,list,selectedItem,collection)
{
    list.innerHTML+="<a class='list-group-item list-group-item-action active' id = '"+functionName+"Saved"+"' aria-current='true'>"+collection[selectedItem]+"</a>";

    for (var i = 0; i<collection.length; i++) {
        list.innerHTML += "<a class='list-group-item list-group-item-action' id = '"+functionName+"Sel"+i+"' onclick="+functionName+"("+i+")>"+collection[i]+"</a>";
    }
}

async function sendLanguage(valueId)
{
    WaitOn();

    await SendRequest(SendRequestOn(sendLanguagee,valueId));

    await WaitOff();
}

async function urlTypeLoad()
{
    var sselectedType = sourceTypes[sourceType];

    

    secondSourceTypeIndex = sourceType+1;

    if(secondSourceTypeIndex>=sourceTypes.length)
    {
        secondSourceTypeIndex = 0;
    }

    urlTypeToggle.textContent = sselectedType;
    document.getElementById('littleSourceInfo').textContent = 'Click to switch to '+sourceTypes[secondSourceTypeIndex]+' mode';
    inputvalue.placeholder = 'Enter '+sselectedType+' Link';
    inputvalue.type = 'url';
}

async function sendTypeUrl()
{
    WaitOn();

    // Add animation class
    urlTypeToggle.classList.add('mode-change-animation');
    
    // Remove animation class after animation completes
    setTimeout(() => {
        urlTypeToggle.classList.remove('mode-change-animation');
    }, 500);
    
    sourceType = secondSourceTypeIndex;
    
    urlTypeLoad();
    
    // Update tooltip content
    const tooltip = bootstrap.Tooltip.getInstance(urlTypeToggle);
    if (tooltip) {
        tooltip.hide();
        tooltip.setContent({ '.tooltip-inner': urlTypeToggle.getAttribute('data-bs-original-title') });
    }

    await SendRequest(SendRequestOn(sendSourceType,sourceType));

    await WaitOff();
}

async function setListBox(list,selectedItem,collection)
{
    list.innerHTML+="<option selected>"+collection[selectedItem]+"</option>";

    for (var i = 0; i<collection.length; i++) {
        list.innerHTML += "<option value='"+i+"'>"+collection[i]+"</option>";
    }
}

async function loadSettingsVideo()
{
    WaitOn();

    let resolu = document.getElementById("videoResolutionList");
    let resoluLive = document.getElementById("videoResolutionLiveList");
    let urlPlayer = document.getElementById("URLPlayerList");
    let youtubePlayer = document.getElementById("youTubePlayerList");
    let youtubePlayerVideo = document.getElementById("youTubePlayerListVideo");
    let livePlayer = document.getElementById("livePlayerList");
    let language = document.getElementById("languagesList");
    let color =  document.getElementById("colorFormatList");
    let radioPlayer = document.getElementById("radioPlayerList");

    // Clear the old elements
    resolu.innerHTML="";
    resoluLive.innerHTML="";
    urlPlayer.innerHTML="";
    youtubePlayer.innerHTML="";
    youtubePlayerVideo.innerHTML="";
    livePlayer.innerHTML="";
    language.innerHTML="";
    color.innerHTML="";
    radioPlayer.innerHTML="";

    const response = await fetchUniversal("Quality");
    const data = JSON.parse(response);
    
    var selectedOptions = data[0];
    var allResolutions = data[1];
    var allPlayerEngines = data[2];
    var allColors = data[3];

    setList(sendVideoResolution.name,resolu,selectedOptions[0],allResolutions);
    setList(sendUrlPlayer.name,urlPlayer,selectedOptions[1],allPlayerEngines);
    setListBox(language,selectedOptions[2],allLanguages);

    hardwareOn = selectedOptions[3]==1;
    
    setList(sendColorFormat.name,color,selectedOptions[4],allColors);

    setList(sendYoutubePlayer.name,youtubePlayer,selectedOptions[5],allPlayerEngines);

    setList(sendLivePlayer.name,livePlayer,selectedOptions[6],allPlayerEngines);
    
    setList(sendYoutubePlayerVideo.name,youtubePlayerVideo,selectedOptions[7],allPlayerEngines);
    
    youtubeCachingOn = selectedOptions[8]==1;

    urlCachingOn = selectedOptions[9]==1;

    setList(sendVideoResolutionLive.name,resoluLive,selectedOptions[10],allResolutions);

    youtubeLegacyPlayerOn = selectedOptions[11]==1;

    checkMacAndSsidOn = selectedOptions[12]==1;
    
    setList(sendRadioPlayer.name,radioPlayer,selectedOptions[13],allPlayerEngines);

    HardwareUp();
    YouTubeCachingUp();
    URLCachingUp();
    YoutubeLegacyPlayerOnUP();
    CheckMacAndSsidOnUP();

    await WaitOff();
}

async function loadDevices()
{
    WaitOn();

    let deviceslist = document.getElementById("devicesList");

    // Clear the old elements
    deviceslist.innerHTML="";

    const response = await fetchUniversal("Devices");
    const data = JSON.parse(response);

    var output = "";
    
    for(var i = 0; i<data.length; i++)
    {

        var deviceName = data[i][0];
        var urlLink = data[i][1];

        output = output+
        "<li class='collection-item' data-url='"+urlLink+"' onclick='loadPage(this)'>" +
        "<div><span class='fw-bold'>"+deviceName+"</span>"+
        "<div class='text-muted small'>"+urlLink+"</div></div></li>";
    }

    deviceslist.innerHTML="";
    deviceslist.insertAdjacentHTML('beforeend',output);

    
    await WaitOff();
}

function loadPage(element) {
    const url = element.getAttribute('data-url');
    window.open(url, '_blank');
}

async function sendHardware()
{
    WaitOn();

    await SendRequest(SendRequestOff(sendHardwaree));
    hardwareOn = !hardwareOn;

    await WaitOff();
    
    HardwareUp();
}


async function sendYoutubeCaching()
{
    WaitOn();

    await SendRequest(SendRequestOff(sendYoutubeCachingg));
    youtubeCachingOn = !youtubeCachingOn;

    await WaitOff();
    
    YouTubeCachingUp();
}

async function sendURLCaching()
{
    WaitOn();

    await SendRequest(SendRequestOff(sendURLCachingg));
    urlCachingOn = !urlCachingOn;

    await WaitOff();
    
    URLCachingUp();
}

async function sendYoutubeLegacyPlayerrOn()
{
    WaitOn();

    await SendRequest(SendRequestOff(sendYoutubeLegacyPlayerrOnn));
    youtubeLegacyPlayerOn = !youtubeLegacyPlayerOn;

    await WaitOff();
    
    YoutubeLegacyPlayerOnUP();
}

async function sendCheckMacAndSsidOn()
{
    WaitOn();

    await SendRequest(SendRequestOff(sendCheckMacAndSsidd));
    checkMacAndSsidOn = !checkMacAndSsidOn;

    await WaitOff();
    
    CheckMacAndSsidOnUP();
}

async function sendLoop()
{
    WaitOn();

    seekPosion = input.value;
    await SendRequest(SendRequestOff(sendLoopp));
    loopOn = !loopOn;

    await WaitOff();
    
    //LoopUp();

    await SetUp();
}

function buttonStoper(a) {
    var b = intParser(input.value) + a;
    if (b < 0) {
        input.value = 0;
    } else {
        if (b > seekMax) {
            b = seekMax;
        }
    }
    input.value = b;
    setSliderValue();
    sendValue();
}

function intParser(a) {
    return parseInt(a, 10);
}
function setType(b, a) {
    return b + a;
}
function addSpace(a) {
    return a + ":";
}
function fixVisual(x)
{
    if(x<10)
    {
        return "0"+x;
    }
    return x;
}
function sliderLoopStarter()
{
    if(timerStoped)
    {
        timerStoped = false;
        sliderLoop();
    }
}
function setSliderValue() {
    var c = intParser(input.value / 60);
    if (c != 0) {
        var b = intParser(input.value % 60);
        var a = intParser(c / 60);
        if (a != 0) {
            c = intParser(c % 60);
            inputvalue.innerHTML = addSpace(a) + addSpace(fixVisual(c)) + setType(fixVisual(b), "H");
        } else {
            inputvalue.innerHTML = addSpace(c) + setType(fixVisual(b), "M");
        }
    } else {
        inputvalue.innerHTML = setType(input.value, "S");
    }
}
function sliderLoop() {
    setTimeout(function () {
        if (timer) {
            input.value++;
            setSliderValue();
            if(input.max==input.value)
            {
                if(loopPlaylist)
                {
                    SetUp();
                }
                else if(loopOn)
                {
                    input.value = 0;
                }
                /*
                else
                {
                    timerOn = false;
                }*/
            }
            sliderLoop();
        }
        else
        {
            timerStoped = true;
        }
    }, 1000);
}

async function WaitOff() {

    await ServerOk();
    
    for (var a = 0; a < buttonsList.length; a++) {
        buttonsList[a].disabled = false;
    }
    for (var aa = 0; aa < inputList.length; aa++) {
        inputList[aa].disabled = false;
    }

    timer = timerState;

    Timer();
}
function WaitOn() {

    timerState = timer;
    timer = false;

    buttonsList = [];
    inputList = [];
    var b = document.getElementsByTagName("button");
    var f = document.getElementsByTagName("input");
    for (var a = 0; a < b.length; a++) {
        if(!b[a].disabled)
        {
            b[a].disabled = true;
            buttonsList.push(b[a]);
        }
    }
    for (var aa = 0; aa < f.length; aa++) {
        if(!f[aa].disabled)
        {
            f[aa].disabled = true;
            inputList.push(f[aa]);
        }
    }
}

input.oninput = function () {
    start = false;
    setSliderValue();
};
input.addEventListener("change", function () {
    sendValue();
});
volumeInput.addEventListener("change", function () {
    sendVolume();
});

PageOpen();

/*
function stoper() {
    start = false;
    wait(true);
    loadingBt();
}

var startpoint = 0;
var character = ".";
var empty = " . . ";
var endpoint = 11 + empty.length;
var time = 40;
function loadingBt() {
    var a = character;
    setTimeout(function () {
        if (endpoint > startpoint) {
            for (let i = 0; i < startpoint; i++) {
                a = a + character;
            }
            a = a + empty;
            for (let i = startpoint + 1; i < endpoint; i++) {
                a = a + character;
            }
            startpoint++;
        } else {
            for (let i = 0; i < endpoint; i++) {
                a = a + character;
            }
            startpoint = 0;
        }
        a = a + character;
        if(setUpOn)
        {
            inputvalue.innerHTML=a;loadingBt();
        }
        else
        {
            inputvalue.setAttribute('disabled', true);
        }
    }, time);
}
*/

async function loadLinkCollectionss() {
    
    const response = await fetchUniversal("Tables");
    const datad = JSON.parse(response);
    let output = "";

    for(let i = 0; i < datad.length; i++) {
        const id = datad[i][0];
        const value = datad[i][1];
        
        output += `
            <div class='card mb-3'> 
                <button type='button' class='btn btn-secondary btn-lg text-start Devices' onclick='OnOff("${i}","${id}")'>
                    ${value}
                </button>
                <ul class='collection-list' id='${id}' hidden>
                    <!-- Elements will be added dynamically -->
                </ul>
            </div>`;
    }

    return output;
}




// Clear all selected codes (main state)
async function refreshAndReboot() {
        WaitOn();
        DisableAll(true);
    
        await SendRequest(SendRequestOff(sendSendLanguagesRefresh));

        await new Promise(resolve => setTimeout(resolve, 5000));
    
        await ServerOk();
    
        await WaitOff();
        
        const modalElement = document.getElementById('newModalV2');
        const modal = bootstrap.Modal.getInstance(modalElement);
        modal.hide();
    
        await SetUp();
}
            
// Confirm selection and update display
async function compactConfirmSelectionAndReboot() {

    WaitOn();
    // Apply temp state to main state
    selectedCodes = [...tempSelectedCodes];
    output = selectedCodes.join(',')
    updateExternalSelectedCodesDisplay();
    hideLanguageSelector();

    
    DisableAll(true);
    
    await SendRequest(SendRequestOn(sendSendLanguagesOfStream,output));

    await new Promise(resolve => setTimeout(resolve, 5000));

    await ServerOk();

    await WaitOff();
    
    const modalElement = document.getElementById('newModalV2');
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();

    await SetUp();

    // In a real application, you would do something with the selected codes here
    //console.log("Selected codes:", selectedCodes);
}

async function loadLinkSources() {
    
    const response = await fetchUniversal("Sources");
    const datad = JSON.parse(response);
    let output = "";
    // Filter the array to keep only elements that do NOT include "_"
    selectedCodes = datad.filter(code => !code.includes("_"));
    isoCodes = allLanguages;
    initCodeSelector();

    for(let i = 0; i < datad.length; i++) {
        const id = datad[i];
        
        output += `
            <div class='card mb-3'> 
                <button type='button' class='btn btn-secondary btn-lg text-start Devices' onclick='OnOffJson("${id}")'>
                    ${id}
                </button>
                <ul class='collection-list' id='${id}' hidden>
                    <!-- Elements will be added dynamically -->
                </ul>
            </div>`;
    }

    return output;
}

async function loadLinkCollections() {
    try {

        const modal = document.getElementById("LinksCollections");
        if (!modal) {
            console.error("LinksCollections element not found");
            return;
        }

        modal.innerHTML = await loadLinkCollectionss()+await loadLinkSources();
    } catch (error) {
        console.error("Error loading link collections:", error);
    }
}

async function loadLinks(collectionId,collectionName, element) {
    try {
        // Check if element already has content to avoid re-fetching
        if (element.hasChildNodes() && element.getAttribute('data-loaded') === 'true') {
            return; // Already loaded, no need to fetch again
        }

        const response = await fetchUniversal(collectionName);
        const data = JSON.parse(response);

        let output = "";

        for(let i = 0; i < data.length; i++) {
            const id = data[i][0];
            let name = data[i][1];
            const link = data[i][2];

            if(name == null || name === "") {
                name = link;
            }

            output += `
                <li class="collection-item">
                    <div class="col-8 Devices" onclick="startFromCollection(${collectionId},${id})">
                        <span class="fw-bold">${name}</span>
                        <div class="text-muted small">${link}</div>
                    </div>
                            
                    <button type="button" class="btn-close Devices" onclick="deleteFromCollection(${collectionId},${id})">
                        <i class="fas fa-times"></i>
                    </button>
                </li>`;
        }

        element.innerHTML = output;
        element.setAttribute('data-loaded', 'true'); // Mark as loaded
        
    } catch (error) {
        console.error("Error loading links:", error);
        element.innerHTML = `<li class="collection-item text-danger">Error loading content: ${error.message}</li>`;
    }
}
async function loadLinksJson(collectionName, element) {
    try {
        // Check if element already has content to avoid re-fetching
        if (element.hasChildNodes() && element.getAttribute('data-loaded') === 'true') {
            return; // Already loaded, no need to fetch again
        }

        const response = await fetchUniversal("Sources/" + collectionName);
        const data = JSON.parse(response);
        
        let output = "";

        for(let i = 0; i < data.length; i++) {
            let name = data[i][0];
            const subCollectionIndex = data[i][1];
            const itemIndexInSubCollection = data[i][2];

            if(name == null || name === "") {
                name = link;
            }

            output += `
                <li class="collection-item">
                    <div class="col-8 Devices" onclick="startFromSource('${collectionName}',${subCollectionIndex},${itemIndexInSubCollection})">
                        <span class="fw-bold">${name}</span>
                    </div>
                </li>`;
        }

        element.innerHTML = output;
        element.setAttribute('data-loaded', 'true'); // Mark as loaded
        
    } catch (error) {
        console.error("Error loading links:", error);
        element.innerHTML = `<li class="collection-item text-danger">Error loading content: ${error.message}</li>`;
    }
}

function OnOff(id,collectionName) {
    const element = document.getElementById(collectionName);
    
    if (element.hidden) {
        // Only load links when showing the element
        loadLinks(id,collectionName, element);
    }
    
    element.hidden = !element.hidden;
}

function OnOffJson(collectionName) {
    const element = document.getElementById(collectionName);
    
    if (element.hidden) {
        // Only load links when showing the element
        loadLinksJson(collectionName, element);
    }
    
    element.hidden = !element.hidden;
}

async function saveVideo()
{
    var sendName = true;
    WaitOn();

    var k = sendSaveVideoInput.value;

    if(k=="")
    {
        sendName = false;
    }

    await SendRequest(SendRequestBool(sendName,sendSaveVideoo,k));
    

    await WaitOff();

    await SetUp();
}


async function startFromCollection(collectionName,id)
{
    WaitOn();

    await SendRequest([sendSendSavedVideoo,collectionName,id]);

    await WaitOff();

    await SetUp();
    
    const modalElement = document.getElementById('newModalV2');
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();
}

async function startFromSource(sourceName,subCollectionIndex,itemIndexInSubCollection)
{
    WaitOn();

    await SendRequest([sendSendFromSource,sourceName,subCollectionIndex,itemIndexInSubCollection]);

    await WaitOff();

    await SetUp();
    
    const modalElement = document.getElementById('newModalV2');
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();
}

async function deleteFromCollection(collectionName,id)
{
    WaitOn();

    await SendRequest([sendDeleteSavedVideoo,collectionName,id]);

    await WaitOff();

    await SetUp();
    
    const modalElement = document.getElementById('newModalV2');
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();
}

async function playlistLoop()
{
    WaitOn();

    seekPosion = input.value;
    await SendRequest(SendRequestOff(sendSendLoopPlaylistt));
    loopPlaylist = !loopPlaylist;

    await WaitOff();

    await SetUp();
}

async function fetchUniversal(url) {
  const response = await fetch(url);
  const buffer = await response.arrayBuffer();
  return new TextDecoder('utf-8').decode(buffer);
}