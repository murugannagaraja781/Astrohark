function formatImageUrl(imgPath, name, SERVER_URL) {
    if (!imgPath) {
        return `https://ui-avatars.com/api/?name=${encodeURIComponent(name || 'User')}&background=random`;
    }
    if (imgPath.startsWith('http')) return imgPath;
    if (SERVER_URL) {
        const path = imgPath.startsWith('/') ? imgPath : `/${imgPath}`;
        return `${SERVER_URL}${path}`;
    }
    return imgPath;
}

module.exports = { formatImageUrl };
