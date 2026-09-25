import {NativeModules, Platform} from 'react-native';

const {FaceAudience} = NativeModules;

export async function prepareFaceAudience() {
  if (Platform.OS !== 'android' || !FaceAudience?.prepare) {
    return false;
  }
  return FaceAudience.prepare();
}

/**
 * @param {string} imagePath local file path from vision-camera takePhoto
 * @returns {Promise<{faceFound:boolean, category?:string, ageYears?:number, gender?:string}>}
 */
export async function classifyAudienceImage(imagePath) {
  if (Platform.OS !== 'android' || !FaceAudience?.classifyImage) {
    return {faceFound: false};
  }
  return FaceAudience.classifyImage(imagePath);
}
